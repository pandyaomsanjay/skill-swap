# Fix Build Errors in skill-swap project

This plan addresses several "Unresolved reference" errors in `AddSkillActivity.kt` and `PlaylistActivity.kt` caused by missing layout elements and mismatched class names.

## Proposed Changes

### 1. Fix Layout for `AddSkillActivity`
The `singleVideoSection` in `activity_add_skill.xml` is incomplete, leading to unresolved references for `btnCancelVideo` and `progressBarVideoUpload`. I will restore the missing fields and sections based on the activity's logic.

#### [MODIFY] [activity_add_skill.xml](file:///D:/AndroidStudioProjects/skill-swap/app/src/main/res/layout/activity_add_skill.xml)
- Restore the `singleVideoSection` with:
    - `TextInputLayout` for `actvCategory`.
    - `TextInputLayout` for `etDescription`.
    - `TextInputLayout` for `etDuration`.
    - A card for Video Selection containing `ivVideoThumbnail`, `tvVideoFileName`, `btnCancelVideo`, and `btnSelectVideo`.
    - A pricing card containing `btnDecrement`, `tvCredits`, and `btnIncrement`.
    - `btnPublishSkill` (MaterialButton).
    - `progressBarVideoUpload` (ProgressBar).

### 2. Fix `PlaylistActivity` and `PlaylistVideoAdapter`
`PlaylistActivity` refers to a missing `PlaylistVideoAdapter` class, while `PlaylistVideoAdapter.kt` contains `PlaylistVideoListAdapter`. I will implement the missing adapter and ensure it matches the viewing requirements.

#### [MODIFY] [PlaylistVideoAdapter.kt](file:///D:/AndroidStudioProjects/skill-swap/app/src/main/java/com/example/sgp/PlaylistVideoAdapter.kt)
- Add the `PlaylistVideoAdapter` class designed for viewing playlists (using `item_playlist_video_row.xml`).
- Keep `PlaylistVideoListAdapter` as it is used in `AddSkillActivity` for managing the video list during creation.

#### [MODIFY] [PlaylistActivity.kt](file:///D:/AndroidStudioProjects/skill-swap/app/src/main/java/com/example/sgp/PlaylistActivity.kt)
- Ensure it correctly uses the new `PlaylistVideoAdapter`.

## Verification Plan

### Automated Tests
- Run `./gradlew :app:compileDebugKotlin` to verify that all unresolved reference errors are resolved.

### Manual Verification
- Deploy the app and navigate to "Add Skill" to ensure the "Single Video" section is displayed correctly and functions as expected.
- Navigate to a playlist to verify that the video list is displayed correctly in `PlaylistActivity`.
