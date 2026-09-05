# Dependencies και άδειες

| Στοιχείο | Έκδοση / προέλευση | Άδεια / χρήση | Περιλαμβάνεται |
|---|---|---|---|
| Vela DSP, Android κώδικας, tests | 0.1.0 validation | MIT, `LICENSE` | Ναι, πλήρης δικός μας source |
| Oboe | `com.google.oboe:oboe:1.9.3` | Apache-2.0, [upstream](https://github.com/google/oboe) | Δηλωμένο dependency· binaries δεν κατέβηκαν |
| DejaVu Sans regular/bold | Εγκατεστημένα αρχεία DejaVu της runtime διανομής | Bitstream Vera notice / DejaVu public-domain additions. Πλήρες Debian copyright αρχείο στο `licenses/` | Ναι, unmodified TTF, περιλαμβάνει ελληνικά |
| Android platform APIs | compile35 / min27 | Platform runtime, όχι αντιγραμμένο SDK ή proprietary DSP | Μόνο χρήση API |
| Android Gradle Plugin | 8.9.2 | Build tooling, Apache-2.0 upstream· transitive graph δεν επιλύθηκε εδώ | Build declaration |
| Gradle | 8.11.1 | Build tooling, Apache-2.0 / upstream third-party notices | Bootstrap script, όχι διανομή |
| Android NDK / CMake | 27.2.12479018 / 3.22.1 | Build tooling με δικές του συνοδευτικές άδειες | Δεν περιλαμβάνονται |
| NumPy / SciPy / Matplotlib | Εκδόσεις στο `evidence/environment.txt` | Host test dependencies, BSD / BSD / Matplotlib license | Δεν ενσωματώνονται στο APK |
| Συνθετικά WAV | Δημιουργήθηκαν από `tests/evaluate.py` | Δικό μας συνθετικό υλικό, MIT όπως το project· κανένα sample ανθρώπου/τραγουδιού | Ναι |

Δεν περιλαμβάνονται Signalsmith, Rubber Band, WORLD, SoundTouch, torchcrepe ή βάρη AI. Οι άδειές τους εξετάστηκαν ως εναλλακτικές, όχι ως ενεργά dependencies. Δεν αντιγράφηκε Antares κώδικας, asset ή proprietary SDK. Το «Auto-Tune» περιγράφει το ζητούμενο του χρήστη· το προϊόν ονομάζεται Vela Tune και δεν δηλώνει σχέση με Antares.

Το `licenses/Oboe-Apache-2.0.txt` παρέχει το πλήρες κείμενο Apache2, και τα ίδια notices βρίσκονται στα Android assets. Πριν από οποιαδήποτε APK διανομή πρέπει να επαληθευθούν τα πραγματικά resolved artifacts, οι transitive dependencies και τα συνοδευτικά notices· εδώ δεν ισχυριζόμαστε ότι επιθεωρήθηκαν binaries που δεν κατέβηκαν.
