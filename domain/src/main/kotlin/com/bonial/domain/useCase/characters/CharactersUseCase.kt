package com.bonial.domain.useCase.characters

import com.bonial.domain.model.network.response.Request
import com.bonial.domain.repository.CharactersPage
import com.bonial.domain.repository.CharactersRepository
import com.bonial.domain.useCase.BaseUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Fetches a page of characters. The repository owns DTO → domain mapping so this
 * use case is a thin pass-through seam — there is no business logic on top of the
 * pagination request today, and keeping the seam preserves an injection point for
 * future behaviour (caching policy, favourites merging, etc.) without ViewModels
 * having to call the repository directly.
 */
class CharactersUseCase @Inject constructor(
    private val repository: CharactersRepository,
) : BaseUseCase<Int, Flow<Request<CharactersPage>>> {

    override suspend fun invoke(params: Any?): Flow<Request<CharactersPage>> {
        val page = params as? Int ?: DEFAULT_PAGE
        return repository.characters(page)
    }

    private companion object {
        const val DEFAULT_PAGE = 1
    }
}
