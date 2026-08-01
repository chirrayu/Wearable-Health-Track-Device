import sys
from pathlib import Path

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

sys.path.append(str(Path(__file__).resolve().parents[1]))

from database import Base, Squad, SoldierModel, get_soldier_by_ref


@pytest.fixture()
def db_session():
    engine = create_engine("sqlite:///:memory:")
    Base.metadata.create_all(bind=engine)
    Session = sessionmaker(bind=engine)
    session = Session()
    try:
        yield session
    finally:
        session.close()


def test_resolve_soldier_by_serial_and_id(db_session):
    squad = Squad(id="squad-1", name="Alpha")
    db_session.add(squad)
    db_session.flush()

    soldier = SoldierModel(
        id="soldier-uuid-1",
        name="John Doe",
        rank_title="Pvt",
        rank_order=1,
        serial="SOLDIER-001",
        suit_id="SUIT-001",
        squad_id=squad.id,
    )
    db_session.add(soldier)
    db_session.commit()

    assert get_soldier_by_ref(db_session, "SOLDIER-001").id == soldier.id
    assert get_soldier_by_ref(db_session, soldier.id).id == soldier.id
    assert get_soldier_by_ref(db_session, "SUIT-001").id == soldier.id
    assert get_soldier_by_ref(db_session, "suit-001").id == soldier.id


def test_pair_suit_logic(db_session):
    from soldiers import pair_suit, PairSuitIn, SoldierCreate

    squad = Squad(id="squad-alpha", name="Alpha")
    db_session.add(squad)
    db_session.flush()

    s1 = SoldierModel(
        id="s-001",
        name="Alice",
        rank_title="Sgt",
        rank_order=2,
        serial="SOLDIER-101",
        squad_id=squad.id
    )
    db_session.add(s1)
    db_session.commit()

    # Pair suit with existing soldier
    res1 = pair_suit(
        body=PairSuitIn(suit_id="SUIT-101", soldier_id="s-001"),
        db=db_session,
        admin="admin"
    )
    assert res1.suit_id == "SUIT-101"
    assert get_soldier_by_ref(db_session, "SUIT-101").name == "Alice"

    # Pair suit with new soldier
    new_s = SoldierCreate(
        name="Bob",
        rank_title="Cpl",
        rank_order=3,
        serial="SOLDIER-102",
        squad_id="squad-alpha",
        role="Medic",
        blood_group="A+"
    )
    res2 = pair_suit(
        body=PairSuitIn(suit_id="SUIT-202", new_soldier=new_s),
        db=db_session,
        admin="admin"
    )
    assert res2.suit_id == "SUIT-202"
    assert res2.name == "Bob"
    assert get_soldier_by_ref(db_session, "SUIT-202").serial == "SOLDIER-102"


