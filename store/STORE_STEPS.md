# «Бу нәрсә?» 0.1.0 — шаги в Google Play Console (вручную, браузер)
Сборка: /workspace/tatar-kupu/release/bu-narsa-0.1.0.aab (подпись — новый ключ, cert SHA-256 D8:43:8B:D9:6A:23:D4:8D:1A:28:33:01:BD:31:5D:97:33:02:10:4F:AE:D4:AF:77:CC:CD:C2:A9:3C:A8:3C:86).
Тексты: store/TEXTS.md · Формы: store/PLAY_FORMS.md · Графика: store/play/ · Политика: https://pirogovlab.ru/bu-narsa/privacy/

Сервисного аккаунта Play Developer API на боксе нет (поиск service_account*.json ничего не нашёл) — всё через консоль. Новое приложение в любом случае создаётся только вручную.
Как публиковались прошлые приложения (по журналу): **сразу Production**, без закрытого тестирования (напр. «Чӑваш сӑмахӗ» 19.10.2026 — Production, 178 стран), аккаунт разработчика 8084969663799476138.

1. Создать приложение: название «Бу нәрсә? Татарский по камере», язык по умолчанию ru-RU, Приложение, Бесплатное.
2. «Содержание приложения» по PLAY_FORMS.md (реклама Да; AD_ID → Advertising; Data safety — только Device or other IDs; фото не отмечать; аудитория 13+).
3. Основная страница: TEXTS.md (ru-RU + перевод en-US); значок play/play_icon_512.png; графика play/feature_graphic_1024x500.png; телефон play/phone_1…5_*.png.
4. Категория Образование; теги Education, Language learning, Dictionary; email mihail.pirogov@gmail.com; сайт https://pirogovlab.ru.
5. Production → Play App Signing (Google-managed) → загрузить AAB → страны (все) → «Отправить на проверку».
## После публикации
- Яндекс Реклама: приложение 20302179 → добавить ссылку на Google Play, подтвердить владение (выход из тестового режима). app-ads.txt на pirogovlab.ru уже содержит строку Яндекса.
- Сайт /bu-narsa/ и README на GitHub: добавить бейдж Google Play.
