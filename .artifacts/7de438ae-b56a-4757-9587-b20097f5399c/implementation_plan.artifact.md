# Fix Supabase Storage Upload Compilation Error

The project fails to build because the `upload` function in the Supabase Storage SDK is being called with an invalid named parameter `upsert`. In version 3.x of the Supabase Kotlin SDK, `upsert` is a property within the `UploadOptionBuilder` DSL, not a direct parameter of the `upload` function.

## Proposed Changes

### [Component Name]

#### [MODIFY] [AddPlaylistVideoActivity.kt](file:///D:/AndroidStudioProjects/skill-swap/app/src/main/java/com/example/sgp/AddPlaylistVideoActivity.kt)

Change the `bucket.upload` call to use the trailing lambda for configuration options.

```kotlin
bucket.upload(path = fileName, data = bytes) {
    upsert = true
}
```

## Verification Plan

### Automated Tests
- Run `:app:compileDebugKotlin` to verify the fix.
