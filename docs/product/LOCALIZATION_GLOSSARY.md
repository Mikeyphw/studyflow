# Localization Glossary

| Code concept | English UI | Portuguese (Brazil) UI |
|---|---|---|
| `StudyPlan` | Study Plan | Plano de estudo |
| `StudyPlanStatus.ACTIVE` | Active | Ativo |
| `StudyPlanStatus.PAUSED` | Paused | Pausado |
| `StudyPlanStatus.COMPLETED` | Completed | Concluído |
| `StudyPlanStatus.ARCHIVED` | Archived | Arquivado |
| `StudyType.PUBLIC_EXAMINATION` | Public examination | Concurso público |
| `StudyType.CERTIFICATION` | Certification | Certificação |
| `StudyType.UNIVERSITY` | University | Universidade |
| `StudyType.ENTRANCE_EXAMINATION` | Entrance examination | Vestibular |
| `StudyType.FREE_STUDY` | Free study | Estudo livre |
| `StudySchedule` | Schedule | Cronograma |
| `MockExam` | Mock exam | Simulado |
| `ReviewItem` | Review | Revisão |
| `Annex` | Annex | Anexo |
| `ErrorNotebook` | Error notebook | Caderno de erros |

## Rules

- English is the base Android resource locale.
- Brazilian Portuguese uses `values-pt-rBR`.
- Resource keys remain English.
- Domain enums and serialized values are never translated.
- User-created names and notes are preserved exactly as entered.
- Sentences must use complete string resources instead of concatenated fragments.
