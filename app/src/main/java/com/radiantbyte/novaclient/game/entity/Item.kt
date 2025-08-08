package com.radiantbyte.novaclient.game.entity

import com.radiantbyte.novaclient.game.entity.Entity

class Item(runtimeEntityId: Long, uniqueEntityId: Long) :
    Entity(runtimeEntityId, uniqueEntityId) {

    override fun toString(): String {
        return "EntityItem(entityId=$runtimeEntityId, uniqueId=$uniqueEntityId, posX=$posX, posY=$posY, posZ=$posZ)"
    }
}