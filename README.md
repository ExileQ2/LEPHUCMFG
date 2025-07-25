# LEPHUCMFG – AB Feature

### What it does
| Action | Endpoint | Android class |
|--------|----------|---------------|
| **Create row** | `POST /api/Ab` (`AbInsertDto`) | `MainActivity` |
| **List rows**  | `GET /api/Ab`  (`AbRow[]`)     | `AbListActivity` + `AbAdapter` |

### File map
| Layer | File | Reason it exists |
|-------|------|------------------|
| Data  | `data/AbInsertDto.kt` | JSON body for POST |
| Data  | `data/AbRow.kt`       | JSON row from GET |
| Net   | `network/AbService.kt`| Retrofit interface (kept thin) |
| Net   | `network/RetrofitClient.kt` | One-time client builder |
| UI    | `MainActivity.kt`     | Collect A, B → POST |
| UI    | `AbListActivity.kt`   | Display list |
| UI    | `ui/AbAdapter.kt`     | RecyclerView binder; swaps id out for cleaner text |

Build: `./gradlew assembleDebug`  
Run tests: *(none yet)*  
Server dev URL: `http://192.168.1.77:5080/swagger`
