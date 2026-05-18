# Phase 5 Summary

## Work completed
- Added realtime call signaling using Socket.IO.
- Implemented a singleton `SocketManager` for persistent WebSocket signaling.
- Added `SignalingRepository` to emit and receive call events.
- Added centralized call state handling with `CallStateManager`.
- Created the call UI screens:
  - `OutgoingCallActivity`
  - `IncomingCallActivity`
  - `CallActivity`
- Added LiveKit token fetching support through `LiveKitManager`.
- Added runtime server URL configuration in `SettingsActivity`.
- Updated the app to launch incoming call UI when a call signal arrives.

## Result
- The app now has a working signaling layer for app-open voice calls, with LiveKit prepared for media joining.

