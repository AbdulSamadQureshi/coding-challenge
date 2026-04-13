package com.bonial.domain.useCase.characters

import com.bonial.domain.model.Character
import com.bonial.domain.model.network.response.Request
import com.bonial.domain.repository.CharactersRepository
import com.bonial.domain.useCase.BaseUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class CharactersResult(
    val characters: List<Character>,
    val totalPages: Int,
)

class CharactersUseCase @Inject constructor(
    private val repository: CharactersRepository,
) : BaseUseCase<Int, Flow<Request<CharactersResult>>> {

    override suspend fun invoke(params: Any?): Flow<Request<CharactersResult>> {
        val page = params as? Int ?: 1
        return repository.characters(page).map { response ->
            when (response) {
                is Request.Loading -> Request.Loading
                is Request.Error -> Request.Error(response.apiError)
                is Request.Success -> {
                    val characters = response.data.results?.map { dto ->
                        Character(
                            id = dto.id,
                            name = dto.name,
                            status = dto.status,
                            species = dto.species,
                            imageUrl = dto.image,
                        )
                    } ?: emptyList()
                    Request.Success(
                        CharactersResult(
                            characters = characters,
                            totalPages = response.data.info?.pages ?: 1,
                        ),
                    )
                }
            }
        }
    }
}
