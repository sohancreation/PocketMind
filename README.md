# 🧠 PocketMind

![PocketMind Banner](assets/banner.png)

## 📥 Download APK
[**Click here to download the latest PocketMind APK**](https://github.com/sohancreation/PocketMind/raw/main/release/app-release.apk)

**PocketMind** is a cutting-edge, privacy-focused Android application that brings the power of Large Language Models (LLMs) directly to your pocket. By running AI models locally on your device, PocketMind ensures that your conversations stay private, secure, and accessible even without an internet connection.

---

## 🚀 Key Features

- **Local-First AI**: All processing happens on-device. No data ever leaves your phone, providing ultimate privacy and low latency.
- **Persistent Memory**: The application features a "User Memory" system that allows the AI to remember context and user preferences across multiple sessions.
- **Modern UI/UX**: Built with Jetpack Compose, offering a sleek, responsive, and intuitive chat interface with a premium dark theme.
- **Offline Functionality**: Chat with your AI anytime, anywhere. No Wi-Fi or data required once the model is initialized.
- **Secure Message History**: Full local database encryption for your chat logs and message entities.

---

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room Persistence Library
- **Local AI Engine**: Custom implementation for on-device inference.
- **Build System**: Gradle (Kotlin DSL)

---

## 📦 Installation & Setup

### Prerequisites
- Android Studio Ladybug (or newer)
- Android SDK 34+
- A device with at least 8GB of RAM for optimal local AI performance.

### Steps
1. **Clone the Repository**:
   ```bash
   git clone https://github.com/sohancreation/PocketMind.git
   ```
2. **Open in Android Studio**:
   Import the project from the root directory.
3. **Download Model**:
   Upon first launch, the app will prompt you to download the optimized model weights (or you can place them in the `_models` directory manually).
4. **Build & Run**:
   Click the **Run** button in Android Studio to deploy to your device.

---

## 📖 Usage

1. **Initialize**: Open the app and wait for the local model to load into memory.
2. **Chat**: Type your query in the message bar.
3. **Context**: The AI will automatically reference past interactions stored in your local "Memory" to provide more personalized responses.

---

## 🤝 Contributing

Contributions are welcome! If you have ideas for optimization or new features:
1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

---

## 📬 Contact

**Sohan Creation** - [@sohancreation](https://github.com/sohancreation)

Project Link: [https://github.com/sohancreation/PocketMind](https://github.com/sohancreation/PocketMind)

---
<p align="center">
  Built with ❤️ for the future of Private AI.
</p>
