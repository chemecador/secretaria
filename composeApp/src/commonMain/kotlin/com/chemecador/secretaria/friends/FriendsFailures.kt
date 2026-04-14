package com.chemecador.secretaria.friends

sealed class FriendsRepositoryException : IllegalStateException()

class FriendUserNotFoundException : FriendsRepositoryException()

class FriendAlreadyExistsException(
    val pending: Boolean,
) : FriendsRepositoryException()

class SelfFriendRequestException : FriendsRepositoryException()
