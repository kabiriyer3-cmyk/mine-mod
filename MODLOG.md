# MODLOG: 0.3.0-silo-rc1

## Новый согласованный вариант

2026-10-24: пользователь уточнил, что нужны обычные быстрые ракеты без космоса, по игровой идее Solo Silo из Helldivers 2. Выбран **компактный вертикальный контейнер**, не машина. Создан новый самостоятельный проект; старые архивы и исходники не перезаписывались.

## Реализовано

- Новые собственные модели контейнера 3,76 блока, одноступенчатой ракеты 3,37 блока и дизайн пульта.
- Привязка целеуказателя через Custom Data, сохранение UUID пусковой и измерения.
- Серверная проверка владения, загруженной пусковой, метки, дальности, высоты и пространства над контейнером.
- Удержание ПКМ: короткая подготовка, пуск, обновление метки. Отпускание завершает управление, оставляет последнюю цель.
- Простая быстрая игровая кинематика с ограниченным поворотом и низким стартовым подъёмом.
- Серверный отрезочный raycast блоков и сущностей, включая пересечение цели между тиками.
- Отдельные состояния сохранения, лимиты времени/дальности/ожидания чанка.
- Собственные обычные эффекты и короткий звук взрыва. Убраны ядерное облако, огромная вспышка, спёкшееся стекло и ступени.
- Малый кратер с ограниченными радиусами, новые настройки `solo-silo.json`.
- Клиент интерполирует серверные позиции; не вычисляет самостоятельную отличающуюся траекторию.
- Новый HUD метки вместо координатного пульта.
- Генераторы, русская/английская локализация, рецепты, workflow и инструкции.

## Проверки

- 8 математических тестов прошли локально.
- Проверка синтаксиса/типов на тестовых API прошла. Настоящая сборка Fabric не выполнялась.
- Ресурсы и размеры проверены. Программное изображение моделей просмотрено.
- Реальные сигнатуры Item usageTick/getMaxUseTime, NbtComponent и RaycastContext сверены с API 1.21.1.
- Вся документация явно разделяет локальные проверки, сборку и тест в игре.

## Ограничения

Нет доступа к установленной игре и компьютеру пользователя. В командной оболочке нет интернет-доступа для зависимостей. Новый JAR не собран, загрузка и визуальный результат в Minecraft не подтверждены.

В ZIP нет чужих игровых ассетов, приватных API-заглушек, оригиналов Minecraft, сохранений или секретов. Репозитории пользователя не менялись, публикация не выполнялась.

Следующий шаг: GitHub Actions, затем TEST_CHECKLIST в копии мира.


## 0.4.0-rc1 (2026-10-24)

- Full art rework requested by the user ("looks terrible"): procedural meshes replaced by CC BY 4.0 artist models (Tomahawk / RickSlash, VLS cell / DenisGLabrecque, Perst-4 / ikoval31) converted by `tools/gen_models.py`.
- New TMSH v2 format: per-part texture, baked AO vertex colour, pivot/axis for animation. Texture LODs with linear filtering.
- Silo is now embedded: entity on the surface, 1x7 shaft column carved on placement and restored on pickup; hatch animation; stowed round visible when the hatch opens.
- Launch sequence with 1.5 s hatch/ignition delay; missile tail starts 6.6 blocks down; roll programme; fins, wings and intake deploy; booster separates as a tumbling mesh.
- New smoke atlas (cauliflower puffs), new launch/flight/impact VFX, new synthesized sounds.
- Designator is a 3D builtin item (Fabric BuiltinItemRendererRegistry); use action NONE (steady hold).
- Approved software preview: docs/silo-preview.jpg.
