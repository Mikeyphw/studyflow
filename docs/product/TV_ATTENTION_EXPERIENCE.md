# TV Attention Experience

StudyFlow TV attention is intentionally foreground-first.

Android TV and Web TV show non-blocking banners while the TV surface is open. These banners are D-pad safe, use large text, and offer TV-safe actions such as Open, Continue, Review, Start, Snooze, Dismiss and Move to phone. All acknowledgement state is written through the shared ATT01 attention ledger so another capable device can avoid duplicate delivery.

Closed-TV reminders are not guaranteed in ATT06. When the TV app or browser is not active, delivery must fall back to a designated phone, desktop, or Web/PWA notifier. ATT07 will choose the active capable device and handle leases; ATT06 only states the TV capability truth and exposes a foreground runtime for both TV implementations.
