package com.tripmate.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tripmate.entity.Attraction;
import com.tripmate.entity.Review;
import com.tripmate.entity.User;
import com.tripmate.mapper.AttractionMapper;
import com.tripmate.mapper.ReviewMapper;
import com.tripmate.mapper.UserMapper;
import com.tripmate.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
    private final ReviewMapper reviewMapper;
    private final UserMapper userMapper;
    private final AttractionMapper attractionMapper;

    @Override
    public Map<String, Object> listByAttraction(Long attractionId, Integer page, Integer size) {
        // 只查顶级评论（parentId IS NULL）
        LambdaQueryWrapper<Review> w = new LambdaQueryWrapper<>();
        w.eq(Review::getAttractionId, attractionId)
         .isNull(Review::getParentId)
         .orderByDesc(Review::getCreatedAt);
        long total = reviewMapper.selectCount(w);
        w.last("LIMIT " + size + " OFFSET " + (page - 1) * size);
        List<Review> records = reviewMapper.selectList(w);

        // 查所有回复
        Set<Long> parentIds = records.stream().map(Review::getId).collect(Collectors.toSet());
        Map<Long, List<Review>> repliesMap = new HashMap<>();
        if (!parentIds.isEmpty()) {
            LambdaQueryWrapper<Review> rw = new LambdaQueryWrapper<>();
            rw.in(Review::getParentId, parentIds).orderByAsc(Review::getCreatedAt);
            List<Review> allReplies = reviewMapper.selectList(rw);
            for (Review reply : allReplies) {
                repliesMap.computeIfAbsent(reply.getParentId(), k -> new ArrayList<>()).add(reply);
            }
        }

        // 批量查用户名
        Set<Long> userIds = new HashSet<>();
        for (Review r : records) { userIds.add(r.getUserId()); }
        for (List<Review> replies : repliesMap.values()) {
            for (Review r : replies) {
                userIds.add(r.getUserId());
                if (r.getReplyToUserId() != null) { userIds.add(r.getReplyToUserId()); }
            }
        }
        Map<Long, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userMapper.selectBatchIds(userIds);
            for (User u : users) { userMap.put(u.getId(), u.getUsername()); }
        }

        // 组装
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Review r : records) {
            enriched.add(toMap(r, userMap, repliesMap));
        }

        return Map.of("records", enriched, "total", total, "page", page, "size", size);
    }

    private Map<String, Object> toMap(Review r, Map<Long, String> userMap, Map<Long, List<Review>> repliesMap) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", r.getId());
        item.put("userId", r.getUserId());
        item.put("username", userMap.getOrDefault(r.getUserId(), "未知用户"));
        item.put("content", r.getContent());
        item.put("rating", r.getRating());
        item.put("createdAt", r.getCreatedAt());
        item.put("parentId", r.getParentId());
        item.put("replyToUserId", r.getReplyToUserId());

        // 嵌套回复
        List<Review> childReplies = repliesMap.getOrDefault(r.getId(), Collections.emptyList());
        List<Map<String, Object>> replyList = new ArrayList<>();
        for (Review reply : childReplies) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("id", reply.getId());
            rm.put("userId", reply.getUserId());
            rm.put("username", userMap.getOrDefault(reply.getUserId(), "未知用户"));
            rm.put("content", reply.getContent());
            rm.put("createdAt", reply.getCreatedAt());
            rm.put("parentId", reply.getParentId());
            rm.put("replyToUserId", reply.getReplyToUserId());
            if (reply.getReplyToUserId() != null) {
                rm.put("replyToUsername", userMap.getOrDefault(reply.getReplyToUserId(), "未知用户"));
            }
            replyList.add(rm);
        }
        item.put("replies", replyList);
        return item;
    }

    @Override
    @Transactional
    public Review create(Long userId, Long attractionId, String content, Integer rating) {
        Review r = new Review();
        r.setUserId(userId);
        r.setAttractionId(attractionId);
        r.setContent(content);
        r.setRating(rating != null && rating >= 1 && rating <= 5 ? rating : 5);
        reviewMapper.insert(r);
        updateAttractionRating(attractionId);
        log.info("评论发表成功 reviewId={} userId={} attractionId={}", r.getId(), userId, attractionId);
        return r;
    }

    @Override
    @Transactional
    public Review reply(Long userId, Long parentReviewId, String content) {
        Review parent = reviewMapper.selectById(parentReviewId);
        if (parent == null) {
            throw new RuntimeException("原评论不存在");
        }

        Review reply = new Review();
        reply.setUserId(userId);
        reply.setAttractionId(parent.getAttractionId());
        reply.setContent(content);
        reply.setRating(null);  // 回复不带评分
        reply.setParentId(parentReviewId);
        reply.setReplyToUserId(parent.getUserId());
        reviewMapper.insert(reply);
        log.info("回复成功 replyId={} parentId={} userId={}", reply.getId(), parentReviewId, userId);
        return reply;
    }

    @Override
    @Transactional
    public void delete(Long reviewId, Long userId, boolean isAdmin) {
        Review r = reviewMapper.selectById(reviewId);
        if (r == null) {
            throw new RuntimeException("评论不存在");
        }
        if (!isAdmin && !r.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除他人评论");
        }

        Long attractionId = r.getAttractionId();

        // 如果是顶级评论，同时删除所有回复
        if (r.getParentId() == null) {
            LambdaQueryWrapper<Review> w = new LambdaQueryWrapper<>();
            w.eq(Review::getParentId, reviewId);
            reviewMapper.delete(w);
        }

        reviewMapper.deleteById(reviewId);
        updateAttractionRating(attractionId);
        log.info("评论删除 reviewId={}", reviewId);
    }

    /** 重新计算景点平均评分（只算顶级评论的评分） */
    private void updateAttractionRating(Long attractionId) {
        LambdaQueryWrapper<Review> w = new LambdaQueryWrapper<>();
        w.eq(Review::getAttractionId, attractionId).isNull(Review::getParentId);
        List<Review> reviews = reviewMapper.selectList(w);
        if (reviews.isEmpty()) {
            return;
        }
        double avg = reviews.stream()
                .mapToInt(Review::getRating)
                .average().orElse(0);
        int rounded = Math.max(1, (int) Math.round(avg));
        Attraction a = attractionMapper.selectById(attractionId);
        if (a != null) {
            a.setRating(rounded);
            attractionMapper.updateById(a);
        }
    }
}
