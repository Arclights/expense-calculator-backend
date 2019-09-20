package com.arclights.expensecalculator.db

import com.arclights.expensecalculator.db.Tables.CATEGORIES
import com.arclights.expensecalculator.db.Tables.PERSONAL_EXPENSE_CORRECTIONS
import com.arclights.expensecalculator.db.Tables.PERSONS
import org.jooq.Record
import org.jooq.impl.DefaultDSLContext
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

@Repository
class PersonalExpensesDao(private val dsl: DefaultDSLContext) {

    fun createUpdatePersonalExpenses(calculationId: UUID, personalExpenses: List<PersonalExpense>): Flux<PersonalExpense> =
            Flux.mergeSequential(personalExpenses.map { createOrUpdatePersonalExpense(calculationId, it) })

    fun createOrUpdatePersonalExpense(calculationId: UUID, personalExpense: PersonalExpense): Mono<PersonalExpense> = Flux
        .mergeSequential(personalExpense.corrections.map {
            createOrUpdatePersonalExpenseCorrection(
                    calculationId,
                    personalExpense.person.id!!,
                    it
            )
        })
        .next()
        .flatMap { getPersonalExpense(calculationId, personalExpense.person.id!!) }

    private fun createOrUpdatePersonalExpenseCorrection(
            calculationId: UUID,
            personId: UUID,
            personalExpenseCorrection: PersonalExpenseCorrection
    ): Mono<Nothing> =
            if (personalExpenseCorrection.id == null)
                createPersonalExpenseCorrection(calculationId, personId, personalExpenseCorrection)
            else
                updatePersonalExpenseCorrection(personalExpenseCorrection)

    private fun createPersonalExpenseCorrection(
            calculationId: UUID,
            personId: UUID,
            personalExpenseCorrection: PersonalExpenseCorrection
    ): Mono<Nothing> = Mono
        .fromCallable {
            dsl.insertInto(
                    PERSONAL_EXPENSE_CORRECTIONS,
                    PERSONAL_EXPENSE_CORRECTIONS.MONTHLY_CALCULATION_ID,
                    PERSONAL_EXPENSE_CORRECTIONS.PERSON_ID,
                    PERSONAL_EXPENSE_CORRECTIONS.AMOUNT,
                    PERSONAL_EXPENSE_CORRECTIONS.COMMENT,
                    PERSONAL_EXPENSE_CORRECTIONS.CATEGORY_ID
            )
                .values(
                        calculationId,
                        personId,
                        personalExpenseCorrection.amount.toDouble(),
                        personalExpenseCorrection.comment,
                        personalExpenseCorrection.category.id
                )
        }
        .map { null }

    private fun updatePersonalExpenseCorrection(personalExpenseCorrection: PersonalExpenseCorrection): Mono<Nothing> = Mono
        .fromCallable {
            dsl.update(PERSONAL_EXPENSE_CORRECTIONS)
                .set(PERSONAL_EXPENSE_CORRECTIONS.AMOUNT, personalExpenseCorrection.amount.toDouble())
                .set(PERSONAL_EXPENSE_CORRECTIONS.COMMENT, personalExpenseCorrection.comment)
                .where(PERSONAL_EXPENSE_CORRECTIONS.ID.eq(personalExpenseCorrection.id))
        }
        .map { null }

    fun getPersonalExpense(calculationId: UUID, personId: UUID): Mono<PersonalExpense> = Flux
        .from(
                dsl.select(
                        PERSONAL_EXPENSE_CORRECTIONS.ID,
                        PERSONAL_EXPENSE_CORRECTIONS.MONTHLY_CALCULATION_ID,
                        PERSONAL_EXPENSE_CORRECTIONS.AMOUNT,
                        PERSONAL_EXPENSE_CORRECTIONS.COMMENT,
                        PERSONS.ID,
                        PERSONS.NAME,
                        CATEGORIES.ID,
                        CATEGORIES.NAME,
                        CATEGORIES.COMMENT
                )
                    .from(PERSONAL_EXPENSE_CORRECTIONS)
                    .leftJoin(PERSONS).on(PERSONS.ID.eq(PERSONAL_EXPENSE_CORRECTIONS.PERSON_ID))
                    .leftJoin(CATEGORIES).on(CATEGORIES.ID.eq(PERSONAL_EXPENSE_CORRECTIONS.CATEGORY_ID))
                    .where(PERSONAL_EXPENSE_CORRECTIONS.MONTHLY_CALCULATION_ID.eq(calculationId))
                    .and(PERSONAL_EXPENSE_CORRECTIONS.PERSON_ID.eq(personId))
        )
        .collectList()
        .map { mapPersonalExpense(it) }
}

private fun mapPersonalExpense(r: List<Record>): PersonalExpense = PersonalExpense(
        mapPerson(r.first()),
        r.map { mapPersonalExpenseCorrection(it) }
)

private fun mapPersonalExpenseCorrection(r: Record): PersonalExpenseCorrection = PersonalExpenseCorrection(
        r.get(PERSONAL_EXPENSE_CORRECTIONS.ID),
        r.get(PERSONAL_EXPENSE_CORRECTIONS.AMOUNT).toBigDecimal(),
        r.get(PERSONAL_EXPENSE_CORRECTIONS.COMMENT),
        mapCategory(r)
)

private fun mapCategory(r: Record): Category = Category(
        r.get(CATEGORIES.ID),
        r.get(CATEGORIES.NAME),
        r.get(CATEGORIES.COMMENT)
)