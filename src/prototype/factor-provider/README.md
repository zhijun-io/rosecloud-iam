# PROTOTYPE — Factor provider interface shape

**Throwaway.** Not production. Answers:

> Can one `FactorProvider` surface (enroll / challenge / revoke / list) plus a
> small registry for Factor kinds express TOTP now and a second Factor later,
> while respecting **MfaFeature**?

## Run

```bash
task prototype:factor-provider
```

Drive the TUI; after each action the full in-memory state reprints.

## Portable bit

`FactorProviderApi.java` is the interface sketch to react to. The TUI and
in-memory fake TOTP are scaffolding.
