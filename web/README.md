# StudyFlow Local Web

StudyFlow Local Web is the mobile-first, self-contained browser edition of StudyFlow.

## W00 contract

- The phone experience is canonical; tablet and desktop layouts enhance it.
- The core product requires no account, backend, cloud database, or network after installation.
- Core user data is locally owned and must remain exportable/importable.
- Primary product loop: **Today → Plan → Practice → Progress**.
- Browser limitations are reported truthfully; Android-only behavior is not simulated or overpromised.
- No remote sync, analytics, ads, or telemetry are introduced by this foundation.

## Architecture progression

The `studyflow_web` Devtool target is preconfigured in `.devtool.toml` as a Node target; W00 adopts it without rewriting repository target configuration. Devtool restore is therefore provided by the Node/npm runner, while build/test/lint come from this package.json. W00 then starts the dependency-free web project scaffold. W01 adds semantic HTML. Later overlays add CSS, vanilla JavaScript, local persistence, IndexedDB/OPFS, PWA support, TypeScript, and React in that order. Domain/planning logic stays independent from the UI framework.

## Devtool

From the repository root:

```sh
devtool --yes --copy -r "$HOME/Code/studyflow" --target studyflow_web validate
```

For local browsing after W01 exists:

```sh
cd web
npm run dev
```

## W04/W05 foundation

W04 introduces the versioned temporary browser-state repository and makes Quick Add durable across reloads. W05 adds the framework-independent Plan/Subject/Topic/Activity/StudySession/Goal/Material/Review/Question/Evidence model plus reference validation and pure selectors. Focus/session evidence remains deferred to W06, and IndexedDB remains deferred to W12.
