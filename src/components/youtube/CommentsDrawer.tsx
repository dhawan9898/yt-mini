import React, { useState } from 'react';
import { usePlayer } from '../../context/PlayerContext';
import { CommentItem } from '../../data/catalog';

interface CommentsDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
}

export const CommentsDrawer: React.FC<CommentsDrawerProps> = ({ isOpen, onClose, title }) => {
  const { comments, addComment } = usePlayer();
  const [newText, setNewText] = useState('');
  const [likedCommentIds, setLikedCommentIds] = useState<Set<string>>(new Set(['c_2']));

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newText.trim()) return;
    addComment(newText.trim());
    setNewText('');
  };

  const toggleCommentLike = (id: string) => {
    setLikedCommentIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  return (
    <div className="comments-drawer-overlay" onClick={onClose}>
      <div className="comments-drawer-panel" onClick={(e) => e.stopPropagation()}>
        {/* Header */}
        <div className="comments-header">
          <div>
            <h3 className="comments-title">Comments</h3>
            <span className="comments-count">{comments.length} comments</span>
          </div>
          <button
            className="icon-glass-btn sm"
            onClick={onClose}
            aria-label="Close Comments"
            type="button"
          >
            <span className="material-symbols-rounded">close</span>
          </button>
        </div>

        {/* Input Row */}
        <form className="comment-input-form" onSubmit={handleSubmit}>
          <img
            src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&q=80"
            alt="You"
            className="comment-author-avatar"
          />
          <input
            type="text"
            className="comment-text-input"
            value={newText}
            onChange={(e) => setNewText(e.target.value)}
            placeholder="Add a comment..."
          />
          <button
            type="submit"
            className="comment-submit-btn"
            disabled={!newText.trim()}
          >
            <span className="material-symbols-rounded">send</span>
          </button>
        </form>

        {/* Comments List */}
        <div className="comments-list">
          {comments.map((c: CommentItem) => {
            const isLiked = likedCommentIds.has(c.id);
            return (
              <div key={c.id} className="comment-item-row">
                <img src={c.avatar} alt={c.author} className="comment-author-avatar" />
                <div className="comment-body-col">
                  <div className="comment-meta-row">
                    <span className="comment-author-name">@{c.author}</span>
                    <span className="comment-time-ago">{c.timeAgo}</span>
                  </div>
                  <p className="comment-text-content">{c.text}</p>
                  <div className="comment-actions-row">
                    <button
                      className={`comment-like-btn ${isLiked ? 'active' : ''}`}
                      onClick={() => toggleCommentLike(c.id)}
                      type="button"
                    >
                      <span className="material-symbols-rounded" style={{ fontSize: '15px' }}>
                        {isLiked ? 'thumb_up' : 'thumb_up'}
                      </span>
                      <span>{c.likes + (isLiked ? 1 : 0)}</span>
                    </button>
                    <button className="comment-dislike-btn" type="button">
                      <span className="material-symbols-rounded" style={{ fontSize: '15px' }}>
                        thumb_down
                      </span>
                    </button>
                    <button className="comment-reply-btn" type="button">
                      Reply
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};
