# Milea: Vehicle Trip & Fuel Tracker

A native Android app for tracking vehicle trips (GPS-based distance, time, routes), fuel fill-ups, and other vehicle expenses, with support for multiple vehicles and a continuous odometer across all of them.

See [`docs/PRD.md`](docs/PRD.md) for the original product requirements this was built against.

## Features

### Trip logging
- Manual **Start Trip / Stop Trip**, plus automatic GPS-based start/stop (Settings → Automatic Trip Detection)
- Live in-progress trip card (distance, duration, current/max speed) while a trip is recording
- Idle handling: auto-detected trips hand back to monitoring after a few minutes idle; manually-started trips keep recording through ordinary stops (traffic, lights) and only have a long safety-net timeout
- Route map per trip (OpenStreetMap tiles via osmdroid), with an offline banner when tiles can't be fetched,  the recorded route itself never depends on connectivity
- Trip categorization (commute / business / leisure / other), with a category filter on the trip list
- Manual trip entry and editing, including an optional starting odometer reading
- Moving time vs. idle time, average/max speed, distance, duration per trip

### Fuel tracking
- Log fill-ups: odometer, fuel amount, price/unit, total cost, date, full-tank flag
- Fully bidirectional calculator, enter any two of {fuel amount, price/unit, total cost} and the third fills in
- Partial fills: set a **% of tank added** (slider or manual entry) using the vehicle's configured tank capacity, instead of needing to know the exact liters/gallons
- Auto-suggested price/unit (from your last fill-up) and auto-suggested odometer (from the latest fill-up, trip, or vehicle offset, always editable)
- Consumption calculation (L/100km or MPG) computed only between consecutive full-tank fill-ups
- Consumption trend chart and monthly fuel spend chart (hand-drawn Compose Canvas, no external charting library)
- Cost per distance, and true cost per distance combining fuel + other expenses

### Expenses
- Log maintenance, insurance, tolls, parking, registration, other, with optional odometer reading shown on each entry
- Edit or delete existing expense entries
- Total expense stat and combined fuel+expense cost-per-distance stat

### Multi-vehicle
- Add, edit, and archive vehicle profiles (make/model/year, fuel type, tank capacity, per-vehicle km/mi unit)
- One vehicle is "selected" at a time for the Dashboard/Quick Actions, independent of how many other vehicles exist or are archived
- Quick vehicle switcher on the Dashboard, with total odometer shown next to the vehicle name
- Odometer offset per vehicle so tracking can start mid-life and stay continuous across trips and fill-ups

### Stats & data
- Dashboard with total distance, trip count, fuel cost/used, average consumption
- CSV export (trips / fill-ups / expenses / all) with share sheet
- Encrypted local backup and restore (password-protected)

### Quality of life
- Per-vehicle km/mi and L/gal unit toggle, applied consistently everywhere
- Dark mode (follows system theme)
- Fully offline trip recording; map tiles cache once viewed and gracefully show a "route without map" state offline

## Not yet implemented

Gaps against the original PRD (`docs/PRD.md`), from a manual review while building this, worth a fresh pass before treating this list as final:

**Trip logging**
- Elevation gain/loss (altitude is captured per GPS point but never aggregated/shown)
- Geofence-based auto-tagging of home/work trips
- Date-range filter on the trip list (category filter exists; the DAO query for date range exists but isn't wired to any UI)
- Harsh braking/acceleration detection
- Configurable idle/auto-start thresholds (currently hardcoded in `TripTrackingService`, not exposed in Settings)

**Fuel tracking**
- Station name and location tagging (the `Fillup` model has the fields; there's no input UI for them yet)
- Receipt photo attachment (same, field exists on both `Fillup` and presumably needed for `Expense`, no picker UI)
- Missed-fill / large-odometer-jump warning
- Best/worst tank stats
- Predictive "fuel running low" estimate
- Regional average price comparison

**Multi-vehicle**
- Home-screen widget for quick start-trip / add-fill-up (Dashboard has an in-app quick switcher; no actual Android widget)
- Cross-vehicle comparison dashboard
- Shared vehicle with multi-user attribution
- Vehicle photo (model field exists, no picker UI in the Add/Edit Vehicle dialog)
- Delete vehicle (repository method exists; no delete action wired up in the UI - only archive)

**Stats & reporting**
- PDF export
- CSV import (for migrating from other trackers)
- Formatted monthly/annual summary report (a monthly spend chart exists; not a full report)

**Reminders & notifications**
- Reminder data model, DAO, and repository exist, but nothing actually schedules or fires a notification yet (no `WorkManager` job despite the dependency being present) - reminders can be stored but won't currently notify you
- Low-fuel notification

**Backup & sync**
- Google Drive backup sync (local encrypted backup/restore works; cloud sync doesn't exist)

## Tech stack

- Kotlin, Jetpack Compose + Material 3
- MVVM + Repository pattern, Hilt for DI
- Room (SQLite) for storage, DataStore for preferences
- Coroutines + Flow throughout
- `FusedLocationProviderClient` in a foreground `Service` for trip tracking
- osmdroid (OpenStreetMap) for the trip map
- Hand-rolled Compose Canvas charts (no charting library dependency)

## Building

Requirements: JDK 17, Android Studio (or the Gradle wrapper directly), minSdk 30.

```
./gradlew assembleDebug
```

The app requests fine/coarse/background location and notification permissions at runtime for trip tracking; the manifest also declares `FOREGROUND_SERVICE_LOCATION` (Android 14+) and network-state access (used only to detect whether map tiles can be fetched - trip recording itself never requires connectivity).

## Known state / before wider release

- **Database schema is at v2** with `fallbackToDestructiveMigration()` - fine pre-release, but replace with a real `Migration` before anyone has data worth keeping across an update.
- No license file yet - add one before making the repo public if that matters to you.
- This README's "not yet implemented" list was compiled from a manual code review, not an automated audit - treat it as a strong starting point, not a guarantee.
