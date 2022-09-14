package com.ivy.core.domain.functions.mapping.entity

import com.ivy.common.time
import com.ivy.core.persistence.entity.trn.TrnEntity
import com.ivy.core.persistence.entity.trn.TrnTimeType
import com.ivy.data.transaction.Transaction
import com.ivy.data.transaction.TrnTime
import java.time.ZoneOffset

fun mapToEntity(trn: Transaction) = with(trn) {
    TrnEntity(
        id = id.toString(),
        accountId = account.id.toString(),
        type = type,
        state = state,
        sync = sync,
        purpose = purpose,
        currency = value.currency,
        amount = value.amount,
        categoryId = category?.id?.toString(),
        title = title,
        description = description,
        // TODO: Check time conversion correctness
        time = time.time().toInstant(ZoneOffset.UTC),
        timeType = if (time is TrnTime.Actual) TrnTimeType.Actual else TrnTimeType.Due,
    )
}