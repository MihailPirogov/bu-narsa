# «Бу нәрсә?» — ответы для форм Google Play Console и RuStore

Пакет `ru.pirogovlab.tatarkamera`, 0.1.0 (1), `release/bu-narsa-0.1.0.aab` (новый собственный ключ загрузки, release/keys/, бэкап /workspace/pirogovlab-games-keys/tatarkamera/; Play App Signing).
Политика: **https://pirogovlab.ru/bu-narsa/privacy/**

Что реально уходит с устройства: только **Yandex Mobile Ads SDK 8.6.0** (баннер R-M-20302179-1, межстраничная R-M-20302179-2): рекламный идентификатор и технические данные для показа рекламы (Yandex Mobile Ads, в составе SDK — AppMetrica). Камера и фото обрабатываются **только на устройстве** (ML Kit с встроенными моделями, TensorFlow Lite) и не передаются; сервера разработчика, аккаунтов, аналитики разработчика и покупок нет. Разрешения: CAMERA, INTERNET, ACCESS_NETWORK_STATE (+AD_ID из SDK). Сохранение в галерею — MediaStore (Pictures/Бу нәрсә) без запроса доступа к файлам (Android 10+).

1. Privacy policy: https://pirogovlab.ru/bu-narsa/privacy/
2. Ads: **Yes**
3. App access: **All functionality available without special access**
4. Advertising ID: **Yes → Advertising or marketing**
5. Target audience: **13–15, 16–17, 18+** (Yandex Ads не в программе Families). Appeal to children: **No**.
6. Data safety: collects/shares **Yes**; encrypted in transit **Yes**; accounts **none**; deletion request **No**.
   Отметить только **Device or other IDs** — Collected Yes, Shared Yes (Яндекс), Optional, цель Advertising or marketing. **Photos/Videos НЕ отмечать** — обработка на устройстве, не передаются (Google: on-device processing ≠ collection). Сверить с ads.yandex.com/helpcenter/en/dev/android/app-privacy-android.
7. IARC: категория **Reference, News, or Educational**; все ответы No (насилия, контента пользователей, геолокации, покупок нет), Ads — Yes. Ожидаемо PEGI 3 / Everyone.
8. Прочее: News No, Government No, Financial None, Health None; foreground service/full-screen intent/exact alarms не используются.
9. Store settings: App → **Education**; теги Education, Language learning, Dictionary, Camera; email mihail.pirogov@gmail.com; сайт https://pirogovlab.ru (app-ads.txt уже содержит `yandex.com, 1969132, DIRECT`); бесплатно.

RuStore: тип **Приложение**, категория **Образование** (доп. — Книги и справочники), 0+, реклама **есть** (Рекламная сеть Яндекса), платного контента нет, файл `release/bu-narsa-0.1.0.apk`.
