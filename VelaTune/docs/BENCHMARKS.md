# Benchmarks — πραγματικά host αποτελέσματα

**Linux x86_64, όχι Android.** GCC13.3, O3, μονοφωνικός DSP, blocks έως 192 samples, σταθερό αρμονικό σήμα. Ο πίνακας δείχνει wall time υπολογισμού, όχι τη χρονική καθυστέρηση ακρόασης.

| Sample rate | Audio διάρκεια | Host wall time | Wall/audio | p99 block | Μέγιστο block | DSP delay |
|---|---:|---:|---:|---:|---:|---:|
| 8000 Hz | 3 s | 16.40 ms | 0.55% | 172.14 μs | 176.54 μs | 48.000 ms |
| 16000 Hz | 3 s | 64.75 ms | 2.16% | 381.54 μs | 590.27 μs | 48.000 ms |
| 44100 Hz | 3 s | 58.14 ms | 1.94% | 156.47 μs | 413.18 μs | 48.005 ms |
| 48000 Hz | 3 s | 38.98 ms | 1.30% | 86.39 μs | 122.01 μs | 48.000 ms |
| 96000 Hz | 3 s | 43.67 ms | 1.46% | 99.56 μs | 197.89 μs | 48.000 ms |

Το delay ελέγχθηκε με impulse στο bypass· η φωνητική μεταφορά έχει επιπλέον φασική/περιοδική συμπεριφορά που δεν περιγράφεται μόνο από ένα impulse. Δεν περιλαμβάνονται ADC/DAC, Android input/output buffering ή Bluetooth.

Στα 48 kHz, 192 frames αντιστοιχούν σε deadline 4 ms. Το χειρότερο host block ήταν περίπου 0,122 ms. Αυτό δεν προβλέπει τον χρόνο σε Android ARM ούτε τη συμπεριφορά του scheduler με screen-off/θερμικό throttling.

9 σταθερά συνθετικά σήματα από περίπου 57 μέχρι 1064 Hz: μέγιστη απόκλιση παραγόμενης νότας 0,575 cent στην ανεξάρτητη εκτίμηση. Ούτε αυτό αποτελεί ποσοστό επιτυχίας σε πραγματικούς τραγουδιστές.

Δέκα λεπτά επαναλαμβανόμενου συνθετικού block επεξεργάστηκαν σε περίπου 8,44 s host wall time. Output πεπερασμένο. Μηδέν dynamic allocations στο μετρημένο DSP process. 50 Java WAV checks επιτυχή. ASan/UBSan stress σε 9 sample rates επιτυχές. Leak, Android lifecycle, battery και θερμοκρασία μη μετρημένα.

Τα ακριβή μη στρογγυλοποιημένα στοιχεία βρίσκονται στα `evidence/dsp-tests.txt` και `evidence/objective-analysis.json`. Το `evidence/signal-analysis.png` είναι αντικειμενική φασματική απεικόνιση, όχι ακουστική αξιολόγηση ανθρώπου.
