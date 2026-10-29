"""transactions 加账单转债务关联列 debt_id。

Revision ID: 0005_transaction_debt_link
Revises: 0004_user_profile_fields
Create Date: 2026-10-28

口径（DEC-056）：账单可「转为债务」，关联记在**账单侧**——`transactions.debt_id`
指向 `debts.id`，NULL = 未转。只加一列、可空、无默认值：
存量 1157+ 行无需回填，旧客户端（payload 无该键）推上来就是 NULL，新客户端读旧云端
数据也为 NULL，双向兼容。不建外键（见 `models.Transaction.debt_id` 注释）。
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0005_transaction_debt_link"
down_revision: str | None = "0004_user_profile_fields"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

#: 与 models.Transaction.debt_id 及 entity_spec 的 max_length 保持一致
DEBT_ID_LEN = 36


def upgrade() -> None:
    op.add_column("transactions", sa.Column("debt_id", sa.String(length=DEBT_ID_LEN), nullable=True))


def downgrade() -> None:
    op.drop_column("transactions", "debt_id")
