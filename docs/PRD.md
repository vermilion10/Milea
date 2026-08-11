# Product Requirements Document: Vehicle Trip & Fuel Tracker

**Platform:** Android (native, Kotlin)\
**Status:** Draft v1.0\
**Author:** vermilion10\
**Date:** August 2026

---

## 1. Overview

A native Android application for tracking vehicle trips (GPS-based distance, time, routes) and fuel/expense history, with support for multiple vehicles per user. The app provides statistical insight into fuel consumption, cost per km, and spending trends, while keeping odometer continuity across the vehicle's lifetime.

### 1.1 Problem Statement
Vehicle owners, especially those tracking mileage for tax/business purposes, or budgeting fuel costs, lack a single tool that combines automatic trip logging with detailed fuel economy analytics across multiple vehicles.

### 1.2 Goals
- Automatically and accurately log trips with minimal user interaction
- Provide accurate fuel consumption statistics (L/100km, MPG, cost/km)
- Support multiple vehicles/profiles with continuous odometer tracking
- Present data through clear graphs and summaries
- Keep all core functionality usable offline

### 1.3 Non-Goals (v1)
- iOS support (may revisit with KMP later)
- Social/sharing features
- OBD-II hardware integration (future consideration)
- Real-time multi-device sync (local-first for v1)

---

## 2. Target Users / Personas

| Persona | Needs |
|---|---|
| **Commuter Chris** | Wants automatic trip logging, doesn't want to think about the app |
| **Budget-conscious Bella** | Wants to see fuel spend trends, cost per km, price comparisons |
| **Multi-car Family (Sam & Jordan)** | Two vehicles, needs to track/compare separately, shared fill-up logging |
| **Freelancer Fatima** | Needs trip categorization (business vs personal) for tax/expense reports |

---

## 3. Feature Requirements

Each feature is tagged **P0** (must-have for v1 launch), **P1** (important, near-term post-launch), or **P2** (nice-to-have / future).

### 3.1 Trip Logging
| Feature | Priority |
|---|---|
| Manual start/stop trip recording | P0 |
| Automatic start/stop via GPS speed threshold | P0 |
| Auto-pause on prolonged idle (configurable timeout) | P0 |
| Record distance, duration, avg/max speed, route polyline | P0 |
| Trip map view (polyline over map) | P0 |
| Trip categorization (commute/business/leisure/other) | P0 |
| Moving time vs. idle time breakdown | P1 |
| Elevation gain/loss | P1 |
| Geofence-based auto-tagging (home/work) | P1 |
| Manual trip entry/edit (for missed trips) | P0 |
| Trip list with filters (date range, vehicle, category) | P0 |
| Harsh braking/acceleration detection | P2 |

### 3.2 Fuel Tracking
| Feature | Priority |
|---|---|
| Log fill-up: odometer, liters, price/unit, total cost, date | P0 |
| Full tank vs. partial fill flag | P0 |
| Auto-suggest price/unit from last entry | P0 |
| Station name + location tagging | P1 |
| Receipt photo attachment | P1 |
| Missed-fill / large-odometer-jump warning | P1 |
| Consumption calculation (L/100km, MPG) between full tanks | P0 |
| Consumption trend graph over time | P0 |
| Cost per km calculation | P0 |
| Best/worst tank stats | P1 |
| Predictive "fuel running low" estimate | P1 |
| Regional average price comparison | P2 |

### 3.3 Expenses (Non-Fuel)
| Feature | Priority |
|---|---|
| Log maintenance, insurance, tolls, parking, other | P1 |
| Expense category breakdown (pie/bar chart) | P1 |
| True cost-per-km (fuel + expenses combined) | P1 |
| Receipt photo attachment | P1 |
| Service/maintenance reminders (by km or date) | P1 |

### 3.4 Multi-Vehicle Support
| Feature | Priority |
|---|---|
| Create/edit/archive vehicle profiles | P0 |
| Per-vehicle odometer offset (continuity across trackers) | P0 |
| Vehicle photo, make/model/year, fuel type, tank capacity | P0 |
| Switch active vehicle quickly (widget/shortcut) | P0 |
| Cross-vehicle comparison dashboard | P1 |
| Shared vehicle with multi-user attribution | P2 |

### 3.5 Statistics & Reporting
| Feature | Priority |
|---|---|
| Dashboard: total km, total spend, avg consumption (per vehicle) | P0 |
| Consumption graph over time | P0 |
| Expenditure graph over time | P0 |
| Monthly/annual summary report | P1 |
| CSV export | P0 |
| PDF export | P1 |
| CSV import (migrate from Fuelio/Drivvo etc.) | P1 |

### 3.6 QoL / Cross-Cutting
| Feature | Priority |
|---|---|
| Home screen widget (start trip / add fill-up) | P1 |
| Unit toggle (km/mi, L/gal) per profile | P0 |
| Dark mode | P0 |
| Offline-first operation | P0 |
| Local encrypted backup / restore | P0 |
| Google Drive backup sync | P1 |
| Notifications: reminders, low fuel estimate | P1 |

---

## 4. Non-Functional Requirements

- **Performance:** GPS point batching to avoid DB write overhead during active trip recording; trip list must render smoothly with 1000+ trips
- **Battery:** Foreground service location updates tuned to balance accuracy vs. battery drain; user-configurable accuracy/frequency
- **Privacy:** All location data stored locally by default; no data leaves device unless user opts into backup/export
- **Reliability:** `START_STICKY` service with state recovery if killed mid-trip by the OS
- **Offline support:** All core features (logging trips, fill-ups, viewing stats) work with no network connection; maps may show cached tiles or fallback to polyline-only view offline
- **Accessibility:** Support Android accessibility services (TalkBack, font scaling)
- **Data integrity:** Foreign-key cascading deletes; consumption calculations must exclude partial fills

---

## 5. Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository pattern |
| Async | Coroutines + Flow |
| DI | Hilt |
| Local DB | Room (SQLite) |
| Preferences | DataStore |
| Location | FusedLocationProviderClient (foreground Service) |
| Maps | Google Maps SDK (or Mapbox/OSMdroid as cost-conscious alternative) |
| Charts | Vico or MPAndroidChart |
| Background jobs | WorkManager |
| Backup | Local JSON/CSV export; optional Google Drive API sync |

---

## 6. Data Model (Summary)

Core entities: `Vehicle`, `Trip`, `TripPoint`, `Fillup`, `Expense`, `Reminder`, related via `vehicleId` foreign keys, with `Trip` → `TripPoint` as a one-to-many for raw GPS data (kept separate from trip summaries for query performance).

Key invariants:
- Consumption stats only calculated between two consecutive **full-tank** fill-ups
- `odometerOffset` on `Vehicle` preserves continuity if tracking starts mid-life or across device changes

---

## 7. Permissions Required

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION` (Android 10+, requested separately with rationale)
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_LOCATION` (Android 14+)
- `POST_NOTIFICATIONS` (Android 13+)
- Storage access only if exporting to shared storage (scoped storage otherwise)

---

## 8. Success Metrics

- % of trips auto-detected vs. manually logged
- Fill-up logging consistency (fill-ups per month per active user)
- 30-day retention
- Crash-free session rate
- Average trips logged per week per user

---

## 9. Milestones / Rollout Plan

| Phase                   | Scope |
|-------------------------|---|
| **M1, Core MVP**        | Vehicle profiles, manual + auto trip logging, fill-up logging, basic consumption stats, CSV export |
| **M2, Stats & Polish**  | Graphs, dashboard, expenses, reminders, widget, dark mode |
| **M3, Backup & Import** | Local/Drive backup, CSV import from competitor apps, PDF export |
| **M4, Advanced**        | Geofencing, predictive range, driving behavior, cross-vehicle comparison |

---

## 10. Open Questions

- Google Maps SDK (better UX, has cost at scale) vs. OSMdroid/Mapbox (cheaper, less polish)
- Should trip auto-detection use only GPS speed, or incorporate Activity Recognition API for better accuracy/battery tradeoff?
- Multi-user shared vehicle (P2), worth scoping now or fully deferring?
- iOS/KMP, revisit after Android v1 traction is validated

---

## 11. Risks

| Risk | Mitigation |
|---|---|
| Background location restrictions vary by OEM (battery optimization killing service) | Add setup guide for common OEMs (Xiaomi, Samsung, etc.), request battery optimization exemption |
| Inaccurate auto trip detection (false starts/stops) | Configurable thresholds, easy manual correction/merge of trips |
| GPS drift affecting distance accuracy | Apply smoothing/Kalman filtering, cross-check against odometer entries when available |
| Data loss (no cloud backup by default) | Prompt for local/Drive backup after first N days of use |
