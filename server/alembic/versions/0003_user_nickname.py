"""users.nickname：用户显示名（改资料用）。

Revision ID: 0003_user_nickname
Revises: 0002_typed_entities
Create Date: 2026-10-12

口径：`nickname` 可空——空 = 未设置显示名，客户端回退显示 `username`。
不动唯一约束（`username` 仍是登录身份，不允许改名：改名会让所有历史引用与用户记忆错位）。
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0003_user_nickname"
down_revision: str | None = "0002_typed_entities"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

#: 与 models.User.nickname 的 String(32) 保持一致
NICKNAME_LEN = 32


def upgrade() -> None:
    op.add_column("users", sa.Column("nickname", sa.String(length=NICKNAME_LEN), nullable=True))


def downgrade() -> None:
    op.drop_column("users", "nickname")
