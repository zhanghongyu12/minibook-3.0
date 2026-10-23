"""users 资料补充：email / bio / region。

Revision ID: 0004_user_profile_fields
Revises: 0003_user_nickname
Create Date: 2026-10-12

口径：三列均可空——空 = 未填写；空串由 `schemas.ProfileUpdateRequest` 归一为 NULL（表示「清除」），
库里不留空字符串。不动唯一约束与登录身份（`username` 仍不可改）。
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0004_user_profile_fields"
down_revision: str | None = "0003_user_nickname"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

#: 与 models.User 的列长度保持一致
EMAIL_LEN = 254
BIO_LEN = 80
REGION_LEN = 32


def upgrade() -> None:
    op.add_column("users", sa.Column("email", sa.String(length=EMAIL_LEN), nullable=True))
    op.add_column("users", sa.Column("bio", sa.String(length=BIO_LEN), nullable=True))
    op.add_column("users", sa.Column("region", sa.String(length=REGION_LEN), nullable=True))


def downgrade() -> None:
    op.drop_column("users", "region")
    op.drop_column("users", "bio")
    op.drop_column("users", "email")
