# Dependencies και άδειες

| Στοιχείο | Έκδοση / προέλευση | Άδεια / χρήση | Περιλαμβάνεται |
|---|---|---|---|
| Vela DSP, Android κώδικας, tests | 0.1.0 validation | MIT, `LICENSE` | Ναι, πλήρης δικός μας source |
| Oboe | `com.google.oboe:oboe:1.9.3` | Apache-2.0, [upstream](https://github.com/google/oboe) | Επιλύθηκε και ενσωματώθηκε στο APK για 3 ABIs |
| DejaVu Sans regular/bold | Εγκατεστημένα αρχεία DejaVu της runtime διανομής | Bitstream Vera notice / DejaVu public-domain additions. Πλήρες Debian copyright αρχείο στο `licenses/` | Ναι, unmodified TTF, περιλαμβάνει ελληνικά |
| Android platform APIs | compile35 / min27 | Platform runtime, όχι αντιγραμμένο SDK ή proprietary DSP | Μόνο χρήση API |
| Android Gradle Plugin | 8.9.2 | Build tooling, Apache-2.0 upstream· εκτελέστηκε στο GitHub Actions | Build declaration |
| Gradle | 8.11.1 | Build tooling, Apache-2.0 / upstream third-party notices | Bootstrap script, όχι διανομή |
| Android NDK / CMake | 27.2.12479018 / 3.22.1 | Build tooling με δικές του συνοδευτικές άδειες | Δεν περιλαμβάνονται |
| NumPy / SciPy / Matplotlib | Εκδόσεις στο `evidence/environment.txt` | Host test dependencies, BSD / BSD / Matplotlib license | Δεν ενσωματώνονται στο APK |
| Συνθετικά WAV | Δημιουργήθηκαν από `tests/evaluate.py` | Δικό μας συνθετικό υλικό, MIT όπως το project· κανένα sample ανθρώπου/τραγουδιού | Ναι |

Δεν περιλαμβάνονται Signalsmith, Rubber Band, WORLD, SoundTouch, torchcrepe ή βάρη AI. Οι άδειές τους εξετάστηκαν ως εναλλακτικές, όχι ως ενεργά dependencies. Δεν αντιγράφηκε Antares κώδικας, asset ή proprietary SDK. Το «Auto-Tune» περιγράφει το ζητούμενο του χρήστη· το προϊόν ονομάζεται Vela Tune και δεν δηλώνει σχέση με Antares.

Το `licenses/Oboe-Apache-2.0.txt` παρέχει το πλήρες κείμενο Apache2, και τα ίδια notices βρίσκονται στα Android assets. Το APK περιλαμβάνει liboboe.so, libvelatune.so και libc++_shared.so για κάθε ABI. Η LLVM/libc++ άδεια περιλαμβάνεται στα licenses/LLVM-libcxx.txt και στα Android assets. Το libc++ είναι ο C++ runtime του NDK με Apache-2.0/LLVM exceptions και legacy notices. Δεν υπάρχει runtime Java dependency πέρα από το Android platform· τα test libraries είναι Android platform legacy test libraries και δεν ενσωματώνονται στον κώδικα της εφαρμογής.
