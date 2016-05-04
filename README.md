# MetaTemp

MetaTemp is an Android App which interfaces with a [MetaWear board from Mbientlabs](https://mbientlab.com/metawear/) to track the current temperature around the MetaWear. The author has a medical device which does not operate properly in very high or very low temperatures, and wrote this app to be able to detect when the temperature was nearing the threshold when it would stop working. The app can be used in any other situation where extreme temperatures must be detected and avoided. The app displays the current temperature, color-coded depending on how high or low it is. These thresholds can be adjusted by the user in the app's preferences. Notifications may be enabled or disabled, and settings also include vibration and ringtone so the user can choose how to be alerted.

## Installation Instructions

1. Download [Android Studio](http://developer.android.com/sdk/index.html).
1. Install Android studio using [these official instructions](http://developer.android.com/sdk/installing/index.html).
1. Start Android Studio using `studio.sh` on the command line.
1. Open the SDK Manager from the Android Studio Quick Start menu by clicking `Configure -> SDK Manager -> Launch Standalone SDK Manager`. Alternatively, if you already have a project open in Android Studio, you can open it from the menu by clicking `Tools -> Android -> SDK Manager`.
1. In the SDK Manager, if any of the following items are not checked, meaning they are not installed, please install them:
  2. Android SDK Tools
  3. The SDK Platform from Android SDK 6.0 (API 23)
  4. Android Support Repository
  5. Android Support Library
  6. Google Repository
1. Clone this repository: `git clone https://github.com/clenk/MetaTemp.git`
1. In Android Studio, from the Quick Start menu select `Import Project (Eclipse ADT, Gradle, etc.)`, then choose the MeteTemp folder created when you clones the repository in the previous step and click `OK`.
1. On your Android phone, ensure that developer options are enabled by going to your phone's settings and scrolling down to the bottom. If `Developer options` is one of the items under `System`, then tap it and make sure it says "On" at the top and not "Off." If `Developer options` is not on the `System` list, then tap `About phone`, scroll down to `Build Number`, and tap it seven times.
1. In `Developer options` make sure that the following settings are enabled:
  2. Stay awake
  3. Enable Bluetooth HCI snoop log
  4. USB debugging
1. Connect your phone to your computer or virtual machine and unlock the phone.
1. In Android Studio, run the app by selecting `Run -> Run 'app'` from the menu, using the keyboard shortcut Shift+F10, or clicking the green 'Run' icon on the toolbar.
