#include <algorithm>
#include <android/log.h>
#include <jni.h>
#include <sstream>
#include <string>
#include <thread>
#include <vector>
#include <atomic>

#include "llama.h"

#define TAG "PocketMind_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static struct llama_model *g_model = nullptr;
static struct llama_context *g_context = nullptr;
static struct llama_sampler *g_sampler = nullptr;
static std::atomic<bool> g_cancel{false};

static bool ends_with(const std::string &s, const std::string &suffix) {
  return s.size() >= suffix.size() &&
         s.compare(s.size() - suffix.size(), suffix.size(), suffix) == 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_localai_chatbot_llmnative_LlamaNative_loadModel(JNIEnv *env,
                                                         jobject /* this */,
                                                         jstring modelPath,
                                                         jint contextSize,
                                                         jint threads) {
  LOGI("loadModel: Start");
  if (g_sampler) {
    llama_sampler_free(g_sampler);
    g_sampler = nullptr;
  }
  if (g_context) {
    llama_free(g_context);
    g_context = nullptr;
  }
  if (g_model) {
    llama_model_free(g_model);
    g_model = nullptr;
  }

  const char *path = env->GetStringUTFChars(modelPath, nullptr);
  llama_backend_init();

  struct llama_model_params model_params = llama_model_default_params();
  model_params.n_gpu_layers = 0;

  g_model = llama_model_load_from_file(path, model_params);
  env->ReleaseStringUTFChars(modelPath, path);

  if (!g_model) {
    LOGE("loadModel: FAILED to load model file.");
    return JNI_FALSE;
  }

  struct llama_context_params ctx_params = llama_context_default_params();
  ctx_params.n_ctx = (uint32_t)contextSize;
  ctx_params.n_threads = (uint32_t)threads;
  ctx_params.n_threads_batch = (uint32_t)threads;

  g_context = llama_init_from_model(g_model, ctx_params);
  if (!g_context) {
    LOGE("loadModel: FAILED to initialize context.");
    return JNI_FALSE;
  }

  // Better defaults for instruction/chat models (greedy makes outputs bland/wrong).
  // Chain order: mild truncation -> penalties -> temperature -> RNG selection.
  auto sparams = llama_sampler_chain_default_params();
  g_sampler = llama_sampler_chain_init(sparams);
  if (!g_sampler) {
    LOGE("loadModel: FAILED to initialize sampler.");
    return JNI_FALSE;
  }

  // Keep a reasonable candidate set and apply light repetition penalty.
  llama_sampler_chain_add(g_sampler, llama_sampler_init_top_k(40));
  llama_sampler_chain_add(g_sampler, llama_sampler_init_top_p(0.95f, 1));
  llama_sampler_chain_add(g_sampler, llama_sampler_init_penalties(/*last_n*/ 64, /*repeat*/ 1.10f, /*freq*/ 0.0f, /*present*/ 0.0f));
  llama_sampler_chain_add(g_sampler, llama_sampler_init_temp(0.80f));
  llama_sampler_chain_add(g_sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

  LOGI("loadModel: SUCCESS. Engine Ready.");
  return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_localai_chatbot_llmnative_LlamaNative_generateStreaming(
    JNIEnv *env, jobject /* this */, jstring prompt, jint maxTokens,
    jobject callback) {

  if (!g_context || !g_model || !g_sampler) {
    LOGE("generateStreaming: Engine not initialized.");
    return;
  }

  jclass callbackClass = env->GetObjectClass(callback);
  jmethodID methodId = env->GetMethodID(callbackClass, "onTokenGenerated",
                                        "(Ljava/lang/String;)V");

  const char *prompt_text = env->GetStringUTFChars(prompt, nullptr);
  std::string prompt_str(prompt_text);
  env->ReleaseStringUTFChars(prompt, prompt_text);

  const struct llama_vocab *vocab = llama_model_get_vocab(g_model);

  // Tokenize
  std::vector<llama_token> tokens(prompt_str.size() + 32);
  int n_tokens = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.size(),
                                tokens.data(), tokens.size(), true, true);
  if (n_tokens < 0) {
    tokens.resize(-n_tokens);
    n_tokens = llama_tokenize(vocab, prompt_str.c_str(), prompt_str.size(),
                              tokens.data(), tokens.size(), true, true);
  }
  tokens.resize(n_tokens);

  if (n_tokens == 0) {
    LOGE("generateStreaming: Zero tokens from prompt.");
    return;
  }

  LOGI("generateStreaming: Starting for %d tokens.", n_tokens);
  g_cancel.store(false);

  // Reset context memory
  llama_memory_seq_rm(llama_get_memory(g_context), -1, -1, -1);
  llama_sampler_reset(g_sampler);

  // Prefill: Process prompt tokens
  // Decode in chunks so we can honor cancellation quickly (important on slow devices/emulators).
  const int k_prefill_chunk = 32;
  for (int i = 0; i < n_tokens; i += k_prefill_chunk) {
    if (g_cancel.load()) {
      LOGI("generateStreaming: Cancel requested during prefill.");
      return;
    }
    const int n_chunk = std::min(k_prefill_chunk, n_tokens - i);
    struct llama_batch batch = llama_batch_init(n_chunk, 0, 1);
    for (int j = 0; j < n_chunk; ++j) {
      const int idx = i + j;
      batch.token[j] = tokens[idx];
      batch.pos[j] = idx;
      batch.n_seq_id[j] = 1;
      batch.seq_id[j][0] = 0;
      batch.logits[j] = (idx == n_tokens - 1);
    }
    batch.n_tokens = n_chunk;

    if (llama_decode(g_context, batch) != 0) {
      LOGE("generateStreaming: Prompt decode failed.");
      llama_batch_free(batch);
      return;
    }
    llama_batch_free(batch);
  }

  // Generation Loop
  int n_generated = 0;
  int n_pos = n_tokens;
  std::string out_buf;

  while (n_generated < maxTokens) {
    if (g_cancel.load()) {
      LOGI("generateStreaming: Cancel requested.");
      break;
    }
    llama_token id = llama_sampler_sample(g_sampler, g_context, -1);
    llama_sampler_accept(g_sampler, id);

    if (llama_vocab_is_eog(vocab, id)) {
      LOGI("generateStreaming: EOS reached.");
      break;
    }

    // Convert token to piece and notify Java
    char piece[512];
    int n_chars =
        llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, true);
    if (n_chars > 0) {
      piece[std::min(n_chars, 511)] = '\0';
      out_buf.append(piece);

      // Stop early if the model starts a new turn/role marker.
      // This prevents ChatML/Gemma tags from leaking into the visible reply.
      if (ends_with(out_buf, "<|user|>") ||
          ends_with(out_buf, "<|system|>") ||
          ends_with(out_buf, "<|assistant|>") ||
          ends_with(out_buf, "<|user") ||
          ends_with(out_buf, "<|system") ||
          ends_with(out_buf, "<|assistant") ||
          ends_with(out_buf, "<start_of_turn>user") ||
          ends_with(out_buf, "<start_of_turn>system") ||
          ends_with(out_buf, "<start_of_turn>model")) {
        LOGI("generateStreaming: Stop marker reached.");
        break;
      }

      jstring jToken = env->NewStringUTF(piece);
      env->CallVoidMethod(callback, methodId, jToken);
      env->DeleteLocalRef(jToken);
    }

    n_generated++;

    // Decode the generated token to get the next one
    struct llama_batch batch = llama_batch_init(1, 0, 1);
    batch.token[0] = id;
    batch.pos[0] = n_pos;
    batch.n_seq_id[0] = 1;
    batch.seq_id[0][0] = 0;
    batch.logits[0] = true;
    batch.n_tokens = 1;

    if (llama_decode(g_context, batch) != 0) {
      LOGE("generateStreaming: Generation decode failed.");
      llama_batch_free(batch);
      break;
    }
    llama_batch_free(batch);
    n_pos++;

    if (n_generated % 5 == 0)
      LOGI("Generated %d tokens...", n_generated);
  }

  LOGI("generateStreaming: Finished. Tokens generated: %d", n_generated);
}

extern "C" JNIEXPORT void JNICALL
Java_com_localai_chatbot_llmnative_LlamaNative_cancelGeneration(JNIEnv *, jobject /* this */) {
  g_cancel.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_localai_chatbot_llmnative_LlamaNative_generate(JNIEnv *env, jobject,
                                                        jstring, jint) {
  return env->NewStringUTF("Streaming implementation active.");
}
