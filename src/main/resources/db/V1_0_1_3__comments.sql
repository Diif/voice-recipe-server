CREATE TABLE IF NOT EXISTS comments
(
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_uid  VARCHAR   NOT NULL REFERENCES users (uid),
    recipe_id BIGINT    NOT NULL REFERENCES recipes (id),
    date      TIMESTAMP NOT NULL,
    content   VARCHAR   NOT NULL
);