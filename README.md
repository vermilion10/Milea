# Milea: Vehicle Trip & Fuel Tracker

A native Android app for tracking vehicle trips (GPS-based distance, time, routes), fuel fill-ups, and other vehicle expenses, with support for multiple vehicles and a continuous odometer across all of them.

See [`docs/PRD.md`](docs/PRD.md) for the original product requirements this was built against.

## Features

### Home
- Odometer at a glance, with a vehicle switcher in the top bar
- One-tap **Start trip**, **Refuel** and **Expense** (the log forms open right on Home)
- **Fuel left estimate**: percentage, liters/gallons remaining and approximate range, calculated from the last full tank, later partial fills, distance driven since, and your average consumption
- This month's distance, spending, fuel and cost per distance, compared with last month
- Recent activity across trips, fill-ups and expenses

### Trip logging
- Manual **Start trip / Stop and save**, plus automatic GPS-based start/stop (Settings → Automatic trip detection)
- **Pre-start checks**: if location is off, the trip is blocked and Google's "turn on location" prompt is offered in place; power saving, battery optimization and approximate-only location are shown as warnings with a one-tap fix, or you can start anyway
- Live trip card on Home and Trips (distance, timer, current/max speed), which survives leaving and reopening the app
- Live notification with distance, time and a **Stop** action; warns if location is switched off mid-trip
- Idle handling: auto-detected trips hand back to monitoring after a few minutes idle; manually started trips keep recording through ordinary stops and only have a long safety-net timeout
- Route map per trip (OpenStreetMap via osmdroid) with an offline banner; the recorded route never depends on connectivity
- Route colored by speed on the map (green under 20 km/h up to red at 100+), with start/finish markers and a legend
- Speed and elevation charts per trip; tapping a point pins that spot on the map
- Time spent in each speed zone, and elevation climb
- Trip categories (commute / business / leisure / other) with a category filter
- Manual trip entry and editing, including an optional starting odometer reading; editing only the category, note or date keeps the recorded distance, times and speeds

### Trip posters
- Strava-style shareable image of any trip: the GPS route traced with a glow, start/end markers, distance, time, average and max speed, date and vehicle
- Styles: Midnight, Ember, Paper, and **Sticker** (transparent background, for overlaying on your own photo)
- Formats: Story (9:16), Portrait (4:5), Square (1:1)
- Share directly to any app or save to `Pictures/Milea`

### Fuel tracking
- Refuel form as a bottom sheet: Full tank / Partial, odometer (checked against the fill-ups before and after that date), price per unit prefilled from last time, then enter **one** of amount paid, fuel amount, or the **fuel gauge** reading (where the needle ended up; the level before refuelling is prefilled from Milea's estimate and can be adjusted); the other value is calculated and shown live
- Optional date, station name and note
- Edit or delete fill-ups, with undo
- Consumption (L/100km or MPG) per full-to-full tank, counting partial fills in between, shown on each fill-up

### Expenses
- Maintenance, insurance, tolls, parking, registration, repairs, other, with date, description and optional odometer
- Edit or delete, with undo; yearly total

### Statistics
- Period selector: 30 days, 3 months, year, all time
- Interactive charts (tap or drag to read values): monthly distance, odometer over time, monthly costs split into fuel and other, and consumption per tank
- **Fill-ups**: count, total fuel, average per fill-up, average / best / worst consumption, average price
- **Costs**: total (fuel + other), lowest and highest bill, average bill, cost per distance, fuel cost per distance
- **Distance**: driven (from odometer and trips), tracked trips, longest trip, per-day and per-month averages

### Multi-vehicle
- Add, edit, archive and restore vehicles (make/model/year, fuel type, tank capacity, per-vehicle km/mi unit)
- Odometer offset per vehicle so tracking can start mid-life and stay continuous across trips, fill-ups and expenses

### Settings & data
- Material 3 design with dynamic color (Android 12+), plus System / Light / Dark theme
- Currency symbol and decimal places (presets for Rp, $, €, £, RM, ¥, ₹), used everywhere amounts are shown
- CSV export (trips / fill-ups / expenses / all) with share sheet
- Encrypted local backup and restore (password-protected)
- Fully offline trip recording

## Not yet implemented

Gaps against the original PRD (`docs/PRD.md`), from a manual review while building this, worth a fresh pass before treating this list as final:

**Trip logging**
- Geofence-based auto-tagging of home/work trips
- Date-range filter on the trip list (category filter exists; the DAO query for date range exists but isn't wired to any UI)
- Harsh braking/acceleration detection
- Configurable idle/auto-start thresholds (currently hardcoded in `TripTrackingService`, not exposed in Settings)

**Fuel tracking**
- Station location tagging (station name can be entered; coordinates are not captured)
- Receipt photo attachment (same, field exists on both `Fillup` and presumably needed for `Expense`, no picker UI)
- Missed-fill / large-odometer-jump warning
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
- Formatted monthly/annual summary report (charts and period stats exist; not an exportable report)

**Reminders & notifications**
- Reminder data model, DAO, and repository exist, but nothing actually schedules or fires a notification yet (no `WorkManager` job despite the dependency being present) - reminders can be stored but won't currently notify you
- Low-fuel notification (the fuel-left estimate is shown in the app but doesn't notify)

**Backup & sync**
- Google Drive backup sync (local encrypted backup/restore works; cloud sync doesn't exist)

## Tech stack

- Kotlin, Jetpack Compose + Material 3
- MVVM + Repository pattern, Hilt for DI
- Room (SQLite) for storage, DataStore for preferences
- Coroutines + Flow throughout
- `FusedLocationProviderClient` in a foreground `Service` for trip tracking
- osmdroid (OpenStreetMap) for the trip map
- Hand-rolled Compose Canvas charts and Android Canvas trip posters (no charting or imaging library)

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
