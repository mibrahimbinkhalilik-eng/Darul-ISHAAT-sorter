# Darul Ishaat Photo Sorter

An Android app that scans photos on your phone using on-device OCR (ML Kit)
and automatically moves any photo containing the word "Darul Ishaat"
(or any keyword you set) into a folder — Pictures/DarulIshaat by default.

## What it does
- Runs a background watcher that notices every new photo saved to your gallery.
- Reads the text inside each photo using Google's on-device text recognition
  (no internet needed, nothing leaves your phone).
- If the text matches your keyword, moves the photo into your chosen folder.
- Also has a "Sort Existing Gallery Photos" button to do a one-time pass over
  photos you already have.

## How to use it on your phone
1. Open the app.
2. Tap "1. Grant Permissions" — allow photo access, notifications, and on
   the next screen turn on "Allow access to manage all files" for this app
   (needed so it can move photos other apps saved, like WhatsApp or Camera).
3. Optionally edit the keyword (default: Darul Ishaat) and destination
   folder name.
4. Tap "2. Start Watching New Photos" — from now on, any new photo whose
   text matches the keyword gets moved automatically.
5. Tap "Sort Existing Gallery Photos" once if you also want it to go
   through everything already in your gallery.

## Notes
- OCR only works well on clear, reasonably legible text.
- The "All files access" permission is intentionally broad — it's what lets
  the app move photos it didn't create itself. Fine for a personal app,
  but not something Google Play allows for public apps.
- Matching is case-insensitive.
