# Vela Tune — 0.1.0 validation

**Υπάρχει πραγματικό υπογεγραμμένο APK δοκιμών. Η επαγγελματική ποιότητα και η φυσική end-to-end latency παραμένουν μη πιστοποιημένες.**
Η εφαρμογή μεταγλωττίστηκε στο GitHub Actions, για ARM64, ARM32 και x86_64, με πραγματικό C++ pitch correction. Πέρασαν Android lint, debug/release compilation, instrumentation APK compilation, υπογραφή και zip alignment. Η ακριβής καταγραφή της εκτέλεσης βρίσκεται στο `docs/BUILD-VALIDATION.md` και στα συνοδευτικά logs.

Δεν ονομάζουμε τα synthetic tests δοκιμή πραγματικού τραγουδιστή. Δεν ονομάζουμε τον χρόνο υπολογισμού end-to-end latency. Το Definition of Done του αιτήματος **δεν έχει επιτευχθεί**.

## Τι περιέχεται

- `app/`: πραγματικό Android UI, μικρόφωνο/Oboe, foreground recording service, presets, εισαγωγή αρχείων, waveform, ακρόαση/seek, Πριν/Μετά, WAV export.
- `dsp/`: ανεξάρτητη υλοποίηση YIN, note selection, Humanize/Hard Tune και pitch-synchronous overlap-add. Δεν υπάρχει προσομοίωση ήχου.
- `tests/`: C++, Java WAV, Python ανεξάρτητη ανάλυση σημάτων και Android instrumentation tests.
- `evidence/`: πραγματικά logs, benchmarks, αποτελέσματα, συνθετικά αρχεία WAV και φασματογράφημα.
- `docs/`: τεχνικές επιλογές, όρια, πραγματικοί/εκκρεμείς έλεγχοι, διαδικασία μέτρησης συσκευής.
- `licenses/`: άδειες κώδικα και γραμματοσειρών.

## Εγκατάσταση και χρήση

1. Άνοιξε το συνοδευτικό `VelaTune-0.1.0-validation.apk` σε Android 8.1 ή νεότερο και επίτρεψε την εγκατάσταση από τον browser/διαχειριστή αρχείων, εάν ζητηθεί. Το ZIP είναι ο πηγαίος κώδικας.
2. Σύνδεση ενσύρματων ή USB ακουστικών.
3. Επιλογή τονικής και κλίμακας. Αν δεν τις γνωρίζεις, η Χρωματική διορθώνει στην κοντινότερη νότα, αλλά δεν γνωρίζει την αρμονία του beat.
4. Ενεργοποίηση ακρόασης και πάτημα Εγγραφή. Η άδεια μικροφώνου ζητείται εκείνη τη στιγμή.
5. Τέλος λήψης → Ηχογραφήσεις → Τελευταία λήψη.
6. Για παλιό αρχείο: Ηχογραφήσεις → Αρχείο → ρυθμίσεις → Εφαρμογή → Πριν/Μετά → Εξαγωγή WAV.

Το «Μετά» είναι το τελευταίο ολοκληρωμένο render. Αλλαγή sliders απαιτεί νέο «Εφαρμογή». Το «Πριν» διατηρείται. Stereo εισαγωγές μετατρέπονται σε mono με μέσο όρο καναλιών· προορισμός είναι μεμονωμένα vocals, όχι ολοκληρωμένα τραγούδια ή διπλές φωνές.

Τα live takes κρατούν το αρχικό DSP preroll περίπου 48 ms και αποθηκεύουν την ουρά κατά το stop, ώστε να μη χαθεί η τελευταία συλλαβή. Για εισαγόμενο αρχείο το offline render αντισταθμίζει την καθυστέρηση του επεξεργαστή και διατηρεί ακριβώς το πλήθος δειγμάτων.

## Τι λειτουργεί χωρίς Internet

Ο σχεδιασμός της εφαρμογής δεν περιλαμβάνει INTERNET permission, λογαριασμό, API, analytics, μοντέλα προς λήψη ή cloud processing. Μετά από επιτυχημένο build/εγκατάσταση ο επεξεργαστής και τα ιδιωτικά αρχεία είναι τοπικά. Ένας εξωτερικός file provider μπορεί να χρειάζεται δικό του Internet για να κατεβάσει επιλεγμένο αρχείο· αυτό δεν είναι απαίτηση του DSP.

## Build για τεχνική συνέχεια

Οι παρακάτω οδηγίες είναι για αναπαραγωγή του build. Η εγκατάσταση και χρήση του APK δεν απαιτούν υπολογιστή.

Απαιτούνται JDK 17, Android SDK 35 / Build Tools 35.0.0, NDK 27.2.12479018, CMake 3.22.1, Gradle 8.11.1 και πρόσβαση σε Google Maven/Maven Central/Gradle distributions. Το AGP είναι 8.9.2, Oboe 1.9.3. Βλ. `app/build.gradle`.

```bash
export ANDROID_HOME=/absolute/path/to/android-sdk
./scripts/build-android.sh
```

Το `gradlew` είναι μικρό source-only bootstrap που κατεβάζει την επίσημη διανομή Gradle και ελέγχει το δημοσιευμένο SHA-256. Δεν προσποιείται ότι περιέχει official wrapper JAR. Εναλλακτικά, άνοιγμα του project σε Android Studio με τις παραπάνω εκδόσεις.

Το build script ζητά lint, debug, release και instrumentation APK compilation και ελέγχει την υπογραφή του debug APK. Το debug package είναι `gr.anelix.velatune.validation`. Το release package `gr.anelix.velatune`, versionCode 1, minSdk 27, targetSdk 35. Το release παραμένει unsigned μέχρι να δοθεί πραγματική release signing identity· δεν βαφτίζουμε debug key «production».

Το εξωτερικό build εκτελέστηκε στο [VocalForge, PR 4](https://github.com/Angel2222522/VocalForge/pull/4), σε ξεχωριστό branch και υποφάκελο VelaTune. Το main και η προηγούμενη εφαρμογή VocalForge δεν αντικαταστάθηκαν. Το `ci-external.yml` είναι το workflow αυτού του build, τοποθετημένο στο root του εξωτερικού repository ως `.github/workflows/vela-validation.yml`.

## Επανάληψη των host tests

```bash
./scripts/verify.sh
```

Χρειάζονται g++/JDK17/Python3 με numpy, scipy, matplotlib. Τα logs υπάρχουν ήδη. Το script εκτελεί DSP tests, ανεξάρτητη ανάλυση, 50 WAV checks, Java syntax parse και ASan/UBSan. Το LeakSanitizer δεν μπορεί να σαρώσει τις διεργασίες στο συγκεκριμένο sandbox· το αρχικό failure διατηρείται ξεχωριστά.

Για ακρόαση των δοκιμαστικών σημάτων, άνοιξε τα WAV στο `evidence/audio/`. Είναι συνθετικά σήματα που κατασκευάστηκαν εδώ, όχι ηχογραφήσεις ανθρώπων ή τραγούδια τρίτων.
