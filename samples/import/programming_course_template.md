# Programming course import template

## Module: Kotlin basics

### Exercise: Safe length

Language: kotlin
Difficulty: 2
Tags: kotlin, null-safety
Run: kotlinc Main.kt
Test: kotlin MainKt

Prompt:
Write a function that returns a nullable string length.

Starter code:
```kotlin
fun safeLength(value: String?): Int = TODO()
```

Expected output:
3

Hint:
Use `?.` and `?:`.

Solution notes:
```kotlin
fun safeLength(value: String?): Int = value?.length ?: 0
```
