-- Инвалидация кэша теплокарты при изменении настроек группы.
--
-- Ключ кэша — heatmap:{groupId}:{grp.version}:{from}:{to}:{granularity}, и до
-- сих пор version двигали только триггеры на availability (V4). Но содержимое
-- ответа зависит ещё и от настройки группы: show_participants решает, приходят
-- в ячейке имена отметившихся или null. Владелец включал галочку, обновлял
-- страницу и не видел ничего — до часа, пока не истечёт TTL, или до первой
-- правки чьего-нибудь расписания. Со стороны это неработающая настройка.
--
-- Триггером, а не строчкой в сервисе. Инвалидация этого кэша уже описана здесь,
-- в БД, и держать её в двух местах — значит однажды поправить одно и забыть
-- другое. Триггер к тому же накрывает пути мимо приложения: правку руками в
-- проде, миграцию, будущий админский эндпоинт. Именно после такой правки
-- протухший кэш ищут дольше всего — в коде-то всё верно.
--
-- BEFORE, а не AFTER: AFTER-триггеру пришлось бы делать UPDATE grp из триггера
-- на grp, то есть заводить рекурсию и глушить её флагом. Здесь достаточно
-- поправить NEW до записи — так же устроен соседний trg_grp_updated_at.
--
-- Условие узкое намеренно. Из полей группы на ответ теплокарты влияет только
-- show_participants: title, description, tz_id, is_locked в неё не попадают
-- (см. AvailabilityService.getHeatmap — там из group читается ровно
-- isShowParticipants). Бампать версию на любое изменение grp значило бы
-- выбрасывать час работы кэша при переименовании группы, а переименование —
-- ровно та правка, которая на теплокарту не влияет никак.
--
-- ЕСЛИ в ответ теплокарты добавится ещё одно поле группы — его нужно добавить
-- и в условие ниже. Другого места, где это связано, нет.

CREATE OR REPLACE FUNCTION bump_group_version()
RETURNS TRIGGER AS $$
BEGIN
    NEW.version := OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_grp_settings_version
    BEFORE UPDATE ON grp
    FOR EACH ROW
    WHEN (OLD.show_participants IS DISTINCT FROM NEW.show_participants)
    EXECUTE FUNCTION bump_group_version();

COMMENT ON COLUMN grp.version IS
    'Incremented for heatmap cache invalidation: by triggers on availability (V4) '
    'and on grp.show_participants (V25)';
