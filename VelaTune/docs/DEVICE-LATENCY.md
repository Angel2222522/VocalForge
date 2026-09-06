# Πρωτόκολλο latency και profiling συσκευής

Δεν υπάρχουν ακόμη physical αποτελέσματα. Οι παρακάτω είναι οδηγίες εκτέλεσης, όχι αποτελέσματα.

Για αξιόπιστο end-to-end απαιτείται κοινή χρονική αναφορά εισόδου/εξόδου: εξωτερικό interface/recorder που καταγράφει ταυτόχρονα stimulus πριν από τη διαδρομή και επιστροφή εξόδου. Ένα USB/ενσύρματο loopback και OboeTester μπορούν να μετρήσουν το baseline audio path. Το τελικό app πρέπει να μετρηθεί ξεχωριστά με bypass και ενεργό correction· το app diagnostics μόνο του δεν μετρά round trip.

- Συσκευή, Android build, SoC, route, sample rate, buffer bursts, ακριβές APK SHA-256.
- 20+ stimuli / cross-correlation καθυστέρηση και διασπορά. Η στάθμη πρέπει να είναι χαμηλή ώστε να μη μικροφωνίζει ή κλιπάρει η είσοδος.
- Δύο κανάλια στην ίδια εξωτερική λήψη: reference / processed. Delay = peak correlation offset / recording sample rate. Η διαδρομή του εξωτερικού recorder πρέπει να χαρακτηρίζεται και να μην παρουσιάζεται ως μηδενική.
- Μεμονωμένο impulse είναι χρήσιμο για unvoiced/bypass delay. Για correction απαιτείται και voiced burst/chirp με γνωστή περιβάλλουσα, επειδή ο επεξεργαστής αλλάζει τη θεμελιώδη.
- Καταγραφή median/p95/p99/max, όχι μόνο ελάχιστης τιμής.
- 30 λεπτά singing/rap, screen on/off. `xruns`, ελλιπείς input αναγνώσεις, FIFO overflow, callback utilization, memory PSS, CPU scheduling, thermal status, μπαταρία με ίδια αρχική κατάσταση.
- Δεν ισοδυναμεί callback utilization 5% με συνολικό CPU5% ή μπαταρία5%.

Η δοκιμή χωρίς loopback hardware δεν μπορεί να μετατραπεί σε πραγματικό end-to-end benchmark με εκτίμηση από buffer sizes. Το μη μετρημένο πεδίο πρέπει να παραμένει «δεν έχει μετρηθεί».
