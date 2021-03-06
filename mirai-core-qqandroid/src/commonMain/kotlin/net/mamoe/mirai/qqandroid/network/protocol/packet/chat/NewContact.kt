/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("INVISIBLE_MEMBER")

package net.mamoe.mirai.qqandroid.network.protocol.packet.chat

import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.readBytes
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.event.events.*
import net.mamoe.mirai.getGroupOrNull
import net.mamoe.mirai.qqandroid.QQAndroidBot
import net.mamoe.mirai.qqandroid.message.contextualBugReportException
import net.mamoe.mirai.qqandroid.network.Packet
import net.mamoe.mirai.qqandroid.network.QQAndroidClient
import net.mamoe.mirai.qqandroid.network.protocol.data.proto.Structmsg
import net.mamoe.mirai.qqandroid.network.protocol.packet.OutgoingPacketFactory
import net.mamoe.mirai.qqandroid.network.protocol.packet.buildOutgoingUniPacket
import net.mamoe.mirai.qqandroid.network.protocol.packet.chat.receive.getNewGroup
import net.mamoe.mirai.qqandroid.utils._miraiContentToString
import net.mamoe.mirai.qqandroid.utils.io.serialization.loadAs
import net.mamoe.mirai.qqandroid.utils.io.serialization.writeProtoBuf

internal class NewContact {

    internal object SystemMsgNewFriend :
        OutgoingPacketFactory<NewFriendRequestEvent?>("ProfileService.Pb.ReqSystemMsgNew.Friend") {

        operator fun invoke(client: QQAndroidClient) = buildOutgoingUniPacket(client) {
            writeProtoBuf(
                Structmsg.ReqSystemMsgNew.serializer(),
                Structmsg.ReqSystemMsgNew(
                    checktype = 2,
                    flag = Structmsg.FlagInfo(
                        frdMsgDiscuss2ManyChat = 1,
                        frdMsgGetBusiCard = 1,
                        frdMsgNeedWaitingMsg = 1,
                        frdMsgUint32NeedAllUnreadMsg = 1,
                        grpMsgMaskInviteAutoJoin = 1
                    ),
                    friendMsgTypeFlag = 1,
                    isGetFrdRibbon = false,
                    isGetGrpRibbon = false,
                    msgNum = 20,
                    version = 1000
                )
            )
        }


        override suspend fun ByteReadPacket.decode(bot: QQAndroidBot): NewFriendRequestEvent? {
            readBytes().loadAs(Structmsg.RspSystemMsgNew.serializer()).run {
                val struct = friendmsgs?.firstOrNull()
                return if (struct == null) null else {
                    struct.msg?.run {
                        NewFriendRequestEvent(
                            bot,
                            struct.msgSeq,
                            msgAdditional,
                            struct.reqUin,
                            groupCode,
                            reqUinNick
                        )
                    }
                }
            }
        }

        internal object Action : OutgoingPacketFactory<Nothing?>("ProfileService.Pb.ReqSystemMsgAction.Friend") {

            operator fun invoke(
                client: QQAndroidClient,
                event: NewFriendRequestEvent,
                accept: Boolean,
                blackList: Boolean = false
            ) =
                buildOutgoingUniPacket(client) {
                    writeProtoBuf(
                        Structmsg.ReqSystemMsgAction.serializer(),
                        Structmsg.ReqSystemMsgAction(
                            actionInfo = Structmsg.SystemMsgActionInfo(
                                type = if (accept) 2 else 3,
                                addFrdSNInfo = Structmsg.AddFrdSNInfo(),
                                msg = "",
                                remark = "",
                                blacklist = !accept && blackList
                            ),
                            msgSeq = event.eventId,
                            reqUin = event.fromId,
                            srcId = 6,
                            subSrcId = 7,
                            subType = 1
                        )
                    )
                }

            override suspend fun ByteReadPacket.decode(bot: QQAndroidBot) = null
        }
    }


    internal object SystemMsgNewGroup :
        OutgoingPacketFactory<Packet?>("ProfileService.Pb.ReqSystemMsgNew.Group") {

        operator fun invoke(client: QQAndroidClient) = buildOutgoingUniPacket(client) {
            writeProtoBuf(
                Structmsg.ReqSystemMsgNew.serializer(),
                Structmsg.ReqSystemMsgNew(
                    checktype = 3,
                    flag = Structmsg.FlagInfo(
                        frdMsgDiscuss2ManyChat = 1,
                        frdMsgGetBusiCard = 0,
                        frdMsgNeedWaitingMsg = 1,
                        frdMsgUint32NeedAllUnreadMsg = 1,
                        grpMsgGetC2cInviteJoinGroup = 1,
                        grpMsgMaskInviteAutoJoin = 1,
                        grpMsgGetDisbandedByAdmin = 1,
                        grpMsgGetOfficialAccount = 1,
                        grpMsgGetPayInGroup = 1,
                        grpMsgGetQuitPayGroupMsgFlag = 1,
                        grpMsgGetTransferGroupMsgFlag = 1,
                        grpMsgHiddenGrp = 1,
                        grpMsgKickAdmin = 1,
                        grpMsgNeedAutoAdminWording = 1,
                        grpMsgNotAllowJoinGrpInviteNotFrd = 1,
                        grpMsgSupportInviteAutoJoin = 1,
                        grpMsgWordingDown = 1
                    ),
                    friendMsgTypeFlag = 1,
                    isGetFrdRibbon = false,
                    isGetGrpRibbon = false,
                    msgNum = 5,
                    version = 1000
                )
            )
        }


        override suspend fun ByteReadPacket.decode(bot: QQAndroidBot): Packet? {
            readBytes().loadAs(Structmsg.RspSystemMsgNew.serializer()).run {
                val struct = groupmsgs?.firstOrNull()

                return if (struct == null) null else struct.msg?.run<Structmsg.SystemMsg, Packet> {
                    //this.soutv("SystemMsg")
                    when (subType) {
                        1 -> { //管理员邀请
                            when (c2cInviteJoinGroupFlag) {
                                1 -> {
                                    // 被邀请入群
                                    BotInvitedJoinGroupRequestEvent(
                                        bot, struct.msgSeq, actionUin,
                                        groupCode, groupName, actionUinNick
                                    )
                                }
                                0 -> {
                                    // 成员申请入群
                                    MemberJoinRequestEvent(
                                        bot, struct.msgSeq, msgAdditional,
                                        struct.reqUin, groupCode, groupName, reqUinNick
                                    )
                                }
                                else -> throw contextualBugReportException(
                                    "parse SystemMsgNewGroup, subType=1",
                                    this._miraiContentToString(),
                                    additional = "并尽量描述此时机器人是否正被邀请加入群, 或者是有有新群员加入此群"
                                )
                            }
                        }
                        2 -> {
                            // 被邀请入群, 自动同意

                            val group = bot.getNewGroup(groupCode) ?: return null
                            val invitor = group[actionUin]

                            BotJoinGroupEvent.Invite(invitor)
                        }
                        5 -> {
                            val group = bot.getGroupOrNull(groupCode) ?: return null
                            val operator = group[actionUin]
                            BotLeaveEvent.Kick(operator)
                        }
                        else -> throw contextualBugReportException(
                            "parse SystemMsgNewGroup",
                            forDebug = this._miraiContentToString(),
                            additional = "并尽量描述此时机器人是否正被邀请加入群, 或者是有有新群员加入此群"
                        )
                    }
                } as Packet // 没有 as Packet 垃圾 kotlin 会把类型推断为Any
            }
        }

        internal object Action : OutgoingPacketFactory<Nothing?>("ProfileService.Pb.ReqSystemMsgAction.Group") {

            operator fun invoke(
                client: QQAndroidClient,
                event: MemberJoinRequestEvent,
                accept: Boolean?,
                blackList: Boolean = false
            ) =
                buildOutgoingUniPacket(client) {
                    writeProtoBuf(
                        Structmsg.ReqSystemMsgAction.serializer(),
                        Structmsg.ReqSystemMsgAction(
                            actionInfo = Structmsg.SystemMsgActionInfo(
                                type = when (accept) {
                                    null -> 14 // ignore
                                    true -> 11 // accept
                                    false -> 12 // reject
                                },
                                groupCode = event.groupId,
                                msg = "",
                                remark = "",
                                blacklist = blackList
                            ),
                            groupMsgType = 1,
                            language = 1000,
                            msgSeq = event.eventId,
                            reqUin = event.fromId,
                            srcId = 3,
                            subSrcId = 31,
                            subType = 1
                        )
                    )
                }

            operator fun invoke(
                client: QQAndroidClient,
                event: BotInvitedJoinGroupRequestEvent,
                accept: Boolean
            ) =
                buildOutgoingUniPacket(client) {
                    writeProtoBuf(
                        Structmsg.ReqSystemMsgAction.serializer(),
                        Structmsg.ReqSystemMsgAction(
                            actionInfo = Structmsg.SystemMsgActionInfo(
                                type = if (accept) 11 else 12,
                                groupCode = Group.calculateGroupCodeByGroupUin(event.groupId)
                            ),
                            groupMsgType = 2,
                            language = 1000,
                            msgSeq = event.eventId,
                            reqUin = event.invitorId,
                            srcId = 3,
                            subSrcId = 10016,
                            subType = 1
                        )
                    )
                }

            override suspend fun ByteReadPacket.decode(bot: QQAndroidBot) = null
        }
    }
}
