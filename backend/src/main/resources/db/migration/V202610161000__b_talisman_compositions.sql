CREATE TABLE talisman_compositions (
    talisman_id UUID PRIMARY KEY REFERENCES talismans(id) ON DELETE CASCADE,
    border_key VARCHAR(100) NOT NULL,
    paper_key VARCHAR(100) NOT NULL,
    animal_key VARCHAR(100) NOT NULL,
    item_key VARCHAR(100) NOT NULL,
    phrase_text VARCHAR(20) NOT NULL,
    phrase_hanja VARCHAR(40) NOT NULL,
    phrase_meaning VARCHAR(200) NOT NULL,
    seal_key VARCHAR(100) NOT NULL,
    CHECK (border_key ~ '^[a-z0-9_]+$'),
    CHECK (paper_key ~ '^[a-z0-9_]+$'),
    CHECK (animal_key ~ '^[a-z0-9_]+$'),
    CHECK (item_key ~ '^[a-z0-9_]+$'),
    CHECK (seal_key ~ '^[a-z0-9_]+$')
);
