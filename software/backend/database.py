#Your database models and connection setup (SQLite for simple start, PostgreSQL for production).
#All other files import from here.

from sqlalchemy import (
    create_engine, Column, String, Integer,
    Float, Boolean, DateTime, Text, ForeignKey
)
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, relationship
from datetime import datetime
from config import DATABASE_URL

# ── Engine + session ──────────────────────────────────────────────
engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False}  # needed for SQLite only
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


# ── Models ────────────────────────────────────────────────────────

class Squad(Base):
    __tablename__ = "squads"

    id         = Column(String, primary_key=True)
    name       = Column(String, unique=True, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow)

    soldiers = relationship("SoldierModel", back_populates="squad_rel")


class SoldierModel(Base):
    __tablename__ = "soldiers"

    id          = Column(String, primary_key=True)
    name        = Column(String, nullable=False)
    rank_title  = Column(String, nullable=False)
    rank_order  = Column(Integer, nullable=False)
    serial      = Column(String, unique=True, nullable=False)
    squad_id    = Column(String, ForeignKey("squads.id"))
    role        = Column(String)
    blood_group = Column(String, default="O+")
    photo_path  = Column(String, nullable=True)
    status      = Column(String, default="stable")
    created_at  = Column(DateTime, default=datetime.utcnow)

    squad_rel   = relationship("Squad", back_populates="soldiers")
    vitals      = relationship("VitalsModel", back_populates="soldier")
    alerts      = relationship("AlertModel", back_populates="soldier")
    location    = relationship("LocationModel", back_populates="soldier")
    suit_config = relationship("SuitConfigModel", back_populates="soldier", uselist=False)


class VitalsModel(Base):
    __tablename__ = "vitals"

    id          = Column(Integer, primary_key=True, autoincrement=True)
    soldier_id  = Column(String, ForeignKey("soldiers.id"), nullable=False)
    hr          = Column(Integer, nullable=True)
    spo2        = Column(Integer, nullable=True)
    temp        = Column(Float, nullable=True)
    battery     = Column(Integer, nullable=True)
    recorded_at = Column(DateTime, default=datetime.utcnow)

    # ── TA-CSS inputs (added to support triage.py scoring) ─────────
    activity_index   = Column(Integer, nullable=True)   # 0-3, from suit accelerometer
    respiratory_rate = Column(Integer, nullable=True)    # breaths/min

    # Blast context: either the suit sends a pre-computed severity (0-0.5),
    # or raw peak-g / duration and the server computes it (see blast.py).
    blast_severity  = Column(Float, nullable=True)       # 0.0-0.5, normalized
    blast_timestamp = Column(DateTime, nullable=True)    # when the blast was detected

    # ── TA-CSS outputs, written by triage.calculate_score() ────────
    score          = Column(Float, nullable=True)
    classification = Column(String, nullable=True)       # "Stable" | "Serious" | "Critical"

    soldier = relationship("SoldierModel", back_populates="vitals")


class AlertModel(Base):
    __tablename__ = "alerts"

    id              = Column(String, primary_key=True)
    soldier_id      = Column(String, ForeignKey("soldiers.id"), nullable=False)
    title           = Column(String, nullable=False)
    severity        = Column(String, nullable=False)
    message         = Column(Text)
    action_required = Column(Boolean, default=False)
    created_at      = Column(DateTime, default=datetime.utcnow)

    soldier = relationship("SoldierModel", back_populates="alerts")


class LocationModel(Base):
    __tablename__ = "locations"

    id          = Column(Integer, primary_key=True, autoincrement=True)
    soldier_id  = Column(String, ForeignKey("soldiers.id"), nullable=False)
    latitude    = Column(Float, nullable=False)
    longitude   = Column(Float, nullable=False)
    recorded_at = Column(DateTime, default=datetime.utcnow)

    soldier = relationship("SoldierModel", back_populates="location")


class SuitConfigModel(Base):
    __tablename__ = "suit_configs"

    soldier_id          = Column(String, ForeignKey("soldiers.id"), primary_key=True)
    hr_sensor           = Column(Boolean, default=True)
    spo2_sensor         = Column(Boolean, default=True)
    temp_sensor         = Column(Boolean, default=True)
    accelerometer       = Column(Boolean, default=True)
    gps_enabled         = Column(Boolean, default=True)
    sampling_rate_secs  = Column(Integer, default=5)
    wifi_enabled        = Column(Boolean, default=True)
    mesh_enabled        = Column(Boolean, default=True)
    radio_gateway       = Column(Boolean, default=False)
    emergency_mode      = Column(Boolean, default=False)
    updated_at          = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    soldier = relationship("SoldierModel", back_populates="suit_config")


class AdminCredential(Base):
    """
    Persistent store for the admin password hash, so that
    POST /auth/change-password can actually take effect.
    Single-row table (id is always "admin") — there is one admin account.
    """
    __tablename__ = "admin_credentials"

    id            = Column(String, primary_key=True, default="admin")
    password_hash = Column(String, nullable=False)
    updated_at    = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


# ── DB init helper ────────────────────────────────────────────────
def init_db():
    Base.metadata.create_all(bind=engine)


# ── Dependency for FastAPI routes ─────────────────────────────────
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()