package com.linggong.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.linggong.dto.ChatMessageDTO;
import com.linggong.dto.ConversationDTO;
import com.linggong.dto.Result;
import com.linggong.entity.ChatConversation;
import com.linggong.entity.ChatMessage;
import com.linggong.entity.Job;
import com.linggong.entity.User;
import com.linggong.mapper.ChatConversationMapper;
import com.linggong.mapper.ChatMessageMapper;
import com.linggong.mapper.JobMapper;
import com.linggong.mapper.UserMapper;
import com.linggong.service.IChatService;
import com.linggong.utils.UserHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 站内沟通（聊天）服务实现。
 *
 * <p>会话双方由岗位推导：工人端（role=0）本人即 worker、雇主为岗位发布者；
 * 雇主端（role=1）本人即 employer、工人为入参 peerId，并校验岗位归属。
 * 所有查询 / 发送都限定在会话参与方范围内，防止越权。
 */
@Service
public class ChatServiceImpl implements IChatService {

    private static final int MAX_CONTENT_LEN = 500;

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final JobMapper jobMapper;
    private final UserMapper userMapper;

    public ChatServiceImpl(ChatConversationMapper conversationMapper, ChatMessageMapper messageMapper,
                           JobMapper jobMapper, UserMapper userMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.jobMapper = jobMapper;
        this.userMapper = userMapper;
    }

    /** 会话双方（workerId + employerId）。 */
    private static final class Party {
        Long workerId;
        Long employerId;
    }

    @Override
    public Result conversation(Long jobId, Long peerId) {
        Party p = new Party();
        Result guard = resolveParty(jobId, peerId, p);
        if (guard != null) {
            return guard;
        }
        ChatConversation c = findConversation(jobId, p.workerId);
        if (c == null) {
            return Result.ok(null);
        }
        return Result.ok(toDTO(c));
    }

    @Override
    public Result conversations() {
        Long me = UserHolder.getUser().getId();
        List<ChatConversation> list = conversationMapper.selectList(new LambdaQueryWrapper<ChatConversation>()
                .and(w -> w.eq(ChatConversation::getWorkerId, me)
                        .or().eq(ChatConversation::getEmployerId, me))
                .orderByDesc(ChatConversation::getLastMessageTime));
        List<ConversationDTO> dtos = new ArrayList<>(list.size());
        for (ChatConversation c : list) {
            dtos.add(toDTO(c));
        }
        return Result.ok(dtos, (long) dtos.size());
    }

    @Override
    public Result messages(Long conversationId, Integer page, Integer pageSize) {
        Long me = UserHolder.getUser().getId();
        ChatConversation c = conversationMapper.selectById(conversationId);
        if (c == null) {
            return Result.fail("会话不存在");
        }
        if (!c.getWorkerId().equals(me) && !c.getEmployerId().equals(me)) {
            return Result.fail("无权查看该会话");
        }
        Page<ChatMessage> p = messageMapper.selectPage(new Page<>(page, pageSize),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByDesc(ChatMessage::getId));

        // 拉取即把对方发来的未读消息置为已读
        ChatMessage upd = new ChatMessage();
        upd.setReadFlag(1);
        messageMapper.update(upd, new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getToUserId, me)
                .eq(ChatMessage::getReadFlag, 0));

        List<ChatMessageDTO> dtos = new ArrayList<>(p.getRecords().size());
        for (ChatMessage m : p.getRecords()) {
            dtos.add(toMsgDTO(m));
        }
        return Result.ok(dtos, p.getTotal());
    }

    @Override
    public Result send(Long jobId, Long peerId, String content) {
        if (content == null || content.trim().isEmpty()) {
            return Result.fail("消息不能为空");
        }
        if (content.trim().length() > MAX_CONTENT_LEN) {
            return Result.fail("消息过长，最多 " + MAX_CONTENT_LEN + " 字");
        }
        Party p = new Party();
        Result guard = resolveParty(jobId, peerId, p);
        if (guard != null) {
            return guard;
        }

        Long me = UserHolder.getUser().getId();
        Long toUserId = me.equals(p.workerId) ? p.employerId : p.workerId;

        // 找或建会话（uk_job_worker 唯一键兜底并发，冲突则重查）
        ChatConversation c = findConversation(jobId, p.workerId);
        if (c == null) {
            c = new ChatConversation();
            c.setJobId(jobId);
            c.setWorkerId(p.workerId);
            c.setEmployerId(p.employerId);
            try {
                conversationMapper.insert(c);
            } catch (DuplicateKeyException e) {
                c = findConversation(jobId, p.workerId);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ChatMessage msg = new ChatMessage();
        msg.setConversationId(c.getId());
        msg.setFromUserId(me);
        msg.setToUserId(toUserId);
        msg.setContent(content.trim());
        msg.setReadFlag(0);
        msg.setCreateTime(now);
        messageMapper.insert(msg);

        c.setLastMessage(msg.getContent());
        c.setLastMessageTime(now);
        conversationMapper.updateById(c);

        return Result.ok(c.getId());
    }

    @Override
    public Result unreadCount() {
        Long me = UserHolder.getUser().getId();
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getToUserId, me)
                .eq(ChatMessage::getReadFlag, 0));
        return Result.ok(count);
    }

    /**
     * 根据岗位 + 对方，解析出会话双方的 workerId / employerId，并做归属校验。
     *
     * @return null 表示校验通过（结果写入 out）；否则为失败 Result
     */
    private Result resolveParty(Long jobId, Long peerId, Party out) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            return Result.fail("岗位不存在");
        }
        Long me = UserHolder.getUser().getId();
        Integer role = UserHolder.getUser().getRole();
        boolean isEmployer = role != null && role == 1;
        if (isEmployer) {
            if (!job.getEmployerId().equals(me)) {
                return Result.fail("只能联系自己岗位的工人");
            }
            if (peerId == null) {
                return Result.fail("请指定联系对象");
            }
            out.workerId = peerId;
            out.employerId = me;
        } else {
            out.workerId = me;
            out.employerId = job.getEmployerId();
        }
        if (out.workerId.equals(out.employerId)) {
            return Result.fail("不能给自己发消息");
        }
        return null;
    }

    private ChatConversation findConversation(Long jobId, Long workerId) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getJobId, jobId)
                .eq(ChatConversation::getWorkerId, workerId));
    }

    /** 会话转 DTO，peek 相对当前登录用户推导「对方」。 */
    private ConversationDTO toDTO(ChatConversation c) {
        Long me = UserHolder.getUser().getId();
        Long peerId = c.getWorkerId().equals(me) ? c.getEmployerId() : c.getWorkerId();

        ConversationDTO dto = new ConversationDTO();
        dto.setId(c.getId());
        dto.setJobId(c.getJobId());
        dto.setPeerId(peerId);
        User peer = userMapper.selectById(peerId);
        if (peer != null) {
            dto.setPeerName(peer.getNickName());
            dto.setPeerIcon(peer.getIcon());
        }
        dto.setLastMessage(c.getLastMessage());
        dto.setLastMessageTime(c.getLastMessageTime() == null ? null : c.getLastMessageTime().toString());
        dto.setUnreadCount(messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, c.getId())
                .eq(ChatMessage::getToUserId, me)
                .eq(ChatMessage::getReadFlag, 0)));
        return dto;
    }

    private ChatMessageDTO toMsgDTO(ChatMessage m) {
        ChatMessageDTO d = new ChatMessageDTO();
        d.setId(m.getId());
        d.setFromUserId(m.getFromUserId());
        d.setContent(m.getContent());
        d.setReadFlag(m.getReadFlag());
        d.setCreateTime(m.getCreateTime() == null ? null : m.getCreateTime().toString());
        return d;
    }
}
