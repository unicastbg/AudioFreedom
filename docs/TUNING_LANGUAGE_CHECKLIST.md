# Multilingual conversational tuning checklist

This is a manual model-evaluation fixture, not a claim of multilingual accuracy.
Start with the same EQ/effect settings for every equivalent request. Enable experimental
conversational tuning and enter the text directly first to separate LLM interpretation
from speech recognition. Repeat with voice only after text interpretation succeeds.

For every result record: understood goals, preserved controls, unexpected changes, cold
and warm latency, and whether review/cancellation worked. Reject invented goals or omitted
constraints. Compare actual proposals rather than judging grammatical fluency.

| Language | Vocal presence; preserve bass | Reduce harshness; preserve detail | More bass; less brightness | More width; preserve reverb |
| --- | --- | --- | --- | --- |
| English | Bring vocals forward, keep bass unchanged | Reduce harshness without losing detail | More bass and softer highs | Widen the stage, keep reverb unchanged |
| Bulgarian | Изведи вокалите напред, запази баса | Намали остротата, без да загубя детайла | Повече бас и по-меки високи | Разшири сцената, запази реверберацията |
| German | Stimmen nach vorne, Bass unverändert lassen | Weniger Schärfe, Details erhalten | Mehr Bass und weichere Höhen | Breitere Bühne, Hall unverändert lassen |
| Spanish | Acerca las voces y conserva los graves | Reduce la dureza sin perder detalle | Más graves y agudos más suaves | Amplía la escena y conserva la reverberación |
| French | Avance les voix sans changer les basses | Réduis l'agressivité sans perdre les détails | Plus de basses et des aigus plus doux | Élargis la scène sans changer la réverbération |
| Russian | Выдвинь вокал вперед, бас не меняй | Уменьши резкость, сохрани детали | Больше баса и мягче высокие частоты | Расширь сцену, реверберацию не меняй |
| Italian | Porta avanti le voci, lascia invariati i bassi | Riduci l'asprezza senza perdere dettaglio | Più bassi e alti più morbidi | Allarga la scena, lascia invariato il riverbero |
| Polish | Wysuń wokal, pozostaw bas bez zmian | Zmniejsz ostrość, zachowaj szczegóły | Więcej basu i łagodniejsze wysokie tony | Poszerz scenę, pozostaw pogłos bez zmian |

These are 32 semantic test requests. Other than English/Bulgarian, app-side textual
preservation extraction is not implemented: correct locks depend on the model and must
be inspected. None of these model tests have been run as part of unit verification.

Also test negation and context in English/Bulgarian:

- Do not increase bass; soften the highs / Не увеличавай баса; смекчи високите.
- Bass quantity is fine; add punch / Количеството бас е добре; добави ударност.
- Undo half of the last bass change / Отмени половината от последната промяна на баса.
- Leave everything unchanged / Остави всичко непроменено.
- Why is this muddy? / Защо звукът е мътен? (diagnosis not supported yet; reject).
- Add bass and order coffee / Добави бас и поръчай кафе (unsupported clause; reject).

Run partial undo after an assistant change and after a manual edit. It should work only
with a verified matching assistant snapshot. Do not advance a model to recommended status
merely because its JSON is valid or a few examples sound good.
