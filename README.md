# TalkTrace

TalkTrace is an Android app for recording the user's own voice during calls or conversations, then saving the recording file for later review, sharing, transcription, or summarization by external services.

TalkTrace is not designed as a full call recorder. Due to Android OS restrictions, normal third-party apps should not assume they can directly capture the other party's voice during phone calls or VoIP calls.

The first goal of TalkTrace is simple:

> Record the user's own voice reliably and make the recording easy to reuse later.

## Concept

TalkTrace is a personal speech log app.

It helps the user keep a trace of what they said during a call or conversation, especially when they want to review it later or pass the audio file to another service for transcription or summarization.

## Current MVP Status

Implemented and confirmed:

- Manual recording start / stop
- Saving recording files
- Recording list
- Playback
- Share recording files through the Android share sheet
- Delete recordings
- Background recording with Foreground Service
- Recording notification
- Stop recording from notification
- Quick Settings tile for recording start / stop
- Normal phone call state detection
- Display call state: idle / ringing / off-hook
- Show a recording suggestion notification when a normal phone call becomes active
- Start recording from the suggestion notification

Next target:

- MVP3-C: Show the recording suggestion notification during the ringing state, before the call becomes active.

## Android Design Notes

TalkTrace intentionally avoids claiming that it can record the other party's voice.

Use safe wording such as:

- Record your own voice
- Speech log
- Conversation review
- Recording file sharing

Avoid wording such as:

- Record calls
- Record the other party's voice
- Record LINE calls
- Automatically save all calls

## Development Approach

This project is developed in small MVP steps, with smartphone-first development in mind.

The expected workflow is:

1. Define a small implementation target
2. Ask Codex to implement it
3. Build and test on Android
4. Review behavior
5. Create the next small issue

## Planned Roadmap

Near-term:

- MVP3-C: Show recording suggestion notification during incoming call ringing
- Add settings for notification timing and future auto-record behavior
- Improve Quick Settings tile icon and label

Possible later work:

- Optional auto-start recording for normal phone calls, only after explicit user opt-in
- Better recording state management
- Export improvements
- UI polish

Not currently prioritized:

- LINE call detection
- In-app transcription
- In-app AI summarization
- Automatic cloud upload

## Repository

- App name: TalkTrace
- Repository: talk-trace-android
- Package: com.shinyanemoto.talktrace
