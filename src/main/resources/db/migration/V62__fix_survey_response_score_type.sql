-- MySQL 8.4 + Hibernate 6.6: o driver passou a reportar TINYINT como Types#TINYINT,
-- e a entidade SurveyResponse declara score como Integer (Types#INTEGER). Com
-- ddl-auto=validate a aplicacao nao sobe:
--   "wrong column type encountered in column [score] in table [survey_responses];
--    found [tinyint], but expecting [integer]"
--
-- O score continua sendo 1..5; INT so alinha o tipo fisico ao tipo da entidade.
-- MODIFY e naturalmente idempotente (rodar de novo em coluna ja INT nao muda nada),
-- por isso nao precisa da checagem no information_schema usada em outras migrations.
ALTER TABLE survey_responses
    MODIFY COLUMN score INT NOT NULL
    COMMENT '1=Muito Insatisfeito, 2=Insatisfeito, 3=Regular, 4=Satisfeito, 5=Muito Satisfeito';
