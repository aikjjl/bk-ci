/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.environment.cron

import com.tencent.devops.common.api.enums.AgentAction
import com.tencent.devops.common.api.enums.AgentStatus
import com.tencent.devops.common.api.util.HashUtil
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.environment.THIRD_PARTY_AGENT_HEARTBEAT_INTERVAL
import com.tencent.devops.environment.dao.NodeDao
import com.tencent.devops.environment.dao.thirdPartyAgent.ThirdPartyAgentDao
import com.tencent.devops.environment.pojo.enums.NodeStatus
import com.tencent.devops.environment.utils.ThirdPartyAgentHeartbeatUtils
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ThirdPartyAgentHeartBeat @Autowired constructor(
    private val dslContext: DSLContext,
    private val thirdPartyAgentDao: ThirdPartyAgentDao,
    private val nodeDao: NodeDao,
    private val thirdPartyAgentHeartbeatUtils: ThirdPartyAgentHeartbeatUtils,
    private val redisOperation: RedisOperation
) {

    @Scheduled(initialDelay = 5000, fixedDelay = 10000)
    fun heartbeat() {
        val lockValue = redisOperation.get(LOCK_KEY)
        if (lockValue != null) {
            logger.info("get lock failed, skip")
            return
        } else {
            redisOperation.set(LOCK_KEY, LOCK_VALUE, 60)
        }
        try {
            checkOKAgent()

            checkExceptionAgent()

            checkUnimportAgent()
        } catch (ignored: Throwable) {
            logger.warn("Fail to check the third party agent heartbeat", ignored)
        } finally {
            redisOperation.delete(LOCK_KEY)
        }
    }

    private fun checkOKAgent() {
        val nodeRecords = thirdPartyAgentDao.listByStatus(dslContext, setOf(AgentStatus.IMPORT_OK))
        if (nodeRecords.isEmpty()) {
            return
        }
        nodeRecords.forEach { record ->
            val heartbeatTime = thirdPartyAgentHeartbeatUtils.getHeartbeatTime(record) ?: return@forEach

            val escape = System.currentTimeMillis() - heartbeatTime
            if (escape > 10 * THIRD_PARTY_AGENT_HEARTBEAT_INTERVAL * 1000) {
                logger.warn("The agent(${HashUtil.encodeLongId(record.id)}) has not receive the heart for $escape ms, mark it as exception")
                dslContext.transaction { configuration ->
                    val context = DSL.using(configuration)
                    thirdPartyAgentDao.updateStatus(
                        context,
                        record.id,
                        null,
                        record.projectId,
                        AgentStatus.IMPORT_EXCEPTION
                    )
                    thirdPartyAgentDao.addAgentAction(context, record.projectId, record.id, AgentAction.OFFLINE.name)
                    val nodeRecord = nodeDao.get(context, record.projectId, record.nodeId)
                    if (nodeRecord == null || nodeRecord.nodeStatus == NodeStatus.DELETED.name) {
                        deleteAgent(context, record.projectId, record.id)
                    }
                    nodeDao.updateNodeStatus(context, record.nodeId, NodeStatus.ABNORMAL)
                }
            }
        }
    }

    private fun checkUnimportAgent() {
        val nodeRecords = thirdPartyAgentDao.listByStatus(
            dslContext,
            setOf(AgentStatus.UN_IMPORT_OK)
        )
        if (nodeRecords.isEmpty()) {
            return
        }
        nodeRecords.forEach { record ->
            val heartbeatTime = thirdPartyAgentHeartbeatUtils.getHeartbeatTime(record) ?: return@forEach
            val escape = System.currentTimeMillis() - heartbeatTime
            if (escape > 2 * THIRD_PARTY_AGENT_HEARTBEAT_INTERVAL * 1000) {
                logger.warn("The un-import agent(${HashUtil.encodeLongId(record.id)}) has not receive the heart for $escape ms, mark it as exception")
                dslContext.transaction { configuration ->
                    val context = DSL.using(configuration)
                    thirdPartyAgentDao.updateStatus(
                        context,
                        record.id,
                        null,
                        record.projectId,
                        AgentStatus.UN_IMPORT,
                        AgentStatus.UN_IMPORT_OK
                    )
                }
            }
        }
    }

    private fun checkExceptionAgent() {
        val exceptionRecord = thirdPartyAgentDao.listByStatus(
            dslContext,
            setOf(AgentStatus.IMPORT_EXCEPTION)
        )
        if (exceptionRecord.isEmpty()) {
            return
        }

        exceptionRecord.forEach { record ->
            val nodeRecord = nodeDao.get(dslContext, record.projectId, record.nodeId)
            if (nodeRecord == null || nodeRecord.nodeStatus == NodeStatus.DELETED.name) {
                deleteAgent(dslContext, record.projectId, record.id)
            }
        }
    }

    private fun deleteAgent(dslContext: DSLContext, projectId: String, agentId: Long) {
        logger.info("Trying to delete the agent($agentId) of project($projectId)")
        thirdPartyAgentDao.delete(dslContext, agentId, projectId)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ThirdPartyAgentHeartBeat::class.java)
        private const val LOCK_KEY = "env_cron_agent_heartbeat_check"
        private const val LOCK_VALUE = "lock"
    }
}