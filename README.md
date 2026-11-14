# SwipeSort

Sort an album by swiping, one photo or video at a time.

| Gesture | What happens |
|---|---|
| Swipe **left** | Moves the item to your "left" album (e.g. *Moments*) |
| Swipe **right** | Moves the item to your "right" album (e.g. *ContentVids*) |
| Swipe **up** | Deletes it (sent to the phone's Trash, recoverable for 30 days) |
| Swipe **down** | Skip, so it stays where it is |
| ↶ button | Undo the last swipe |

Requires Android 11 or newer.

## Using it
1. Pick the **album to sort** (you can filter to Videos only or Photos only).
2. Pick the **left** and **right** albums. Choose any existing album, or tap **Create new album**
   (it's made in `Pictures/<name>` the first time something is moved there). Use ⇄ to swap sides.
   Change them any time you like; the app remembers your last choice.
3. Tap **Start sorting** and swipe.

Swipes are saved to your gallery in batches (every 25 swipes, when you tap **Apply**, or when you finish).
Until then you can undo them.

**Fast mode (recommended):** On the setup screen tap *Open settings* and allow **Media management**
for SwipeSort. Otherwise Android shows a confirmation pop-up for every batch.

## Getting the APK (no computer needed)
1. Create a new repository on github.com and upload all these files (keep the folder structure,
   including the hidden `.github` folder).
2. GitHub builds the app automatically (Actions tab, about 4–5 minutes).
3. Open the repo's **Releases** page on your phone, download `SwipeSort.apk`, and install it
   (allow "install unknown apps" for your browser when asked).

Or open the folder in Android Studio and press Run.
