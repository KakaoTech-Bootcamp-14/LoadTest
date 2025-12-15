import React, { useState, useEffect, useCallback, forwardRef } from 'react';
import { Avatar } from '@vapor-ui/core';
import { generateColorFromEmail, getContrastTextColor } from '@/utils/colorUtils';
import { useAuth } from '@/contexts/AuthContext';

/**
 * CustomAvatar 컴포넌트
 * 
 * @param {Object} props
 * @param {Object} props.user - 사용자 객체 (name, email, profileImage 필드)
 * @param {string} props.size - 아바타 크기 ('sm' | 'md' | 'lg' | 'xl')
 * @param {Function} props.onClick - 클릭 핸들러 (있으면 button으로 렌더링)
 * @param {string} props.src - 프로필 이미지 URL (user.profileImage 대신 직접 지정 가능)
 * @param {boolean} props.showImage - 이미지 표시 여부 (기본값: true)
 * @param {boolean} props.persistent - 실시간 프로필 업데이트 감지 여부 (기본값: false)
 * @param {boolean} props.showInitials - 이니셜 표시 여부 (기본값: true)
 * @param {string} props.className - 추가 CSS 클래스
 * @param {Object} props.style - 추가 인라인 스타일
 */
const CustomAvatar = forwardRef(({
  user,
  size = 'md',
  onClick,
  src,
  showImage = true,
  persistent = false,
  showInitials = true,
  className = '',
  style = {},
  ...props
}, ref) => {
  const { user: authUser } = useAuth();
  // persistent 모드일 때만 상태 관리
  const [currentImage, setCurrentImage] = useState('');
  const [imageError, setImageError] = useState(false);

  // 이메일 기반 배경색/텍스트 색상 생성
  const backgroundColor = generateColorFromEmail(user?.email);
  const color = getContrastTextColor(backgroundColor);

  // 파일 뷰 엔드포인트에 인증 쿼리 파라미터를 붙여서 이미지가 차단되지 않도록 처리
  const appendAuthParams = useCallback((url) => {
    const token = user?.token || authUser?.token;
    const sessionId = user?.sessionId || authUser?.sessionId;

    if (!url || !token) return url;
    if (!url.includes('/api/files/')) return url;
    if (url.includes('token=')) return url;

    const params = new URLSearchParams();
    params.set('token', token);
    if (sessionId) {
      params.set('sessionId', sessionId);
    }

    const delimiter = url.includes('?') ? '&' : '?';
    return `${url}${delimiter}${params.toString()}`;
  }, [user?.token, user?.sessionId, authUser?.token, authUser?.sessionId]);

  // 프로필 이미지 URL 생성 (memoized)
  const getImageUrl = useCallback((imagePath) => {
    // src prop이 직접 제공된 경우
    if (src) return src;
    
    if (!imagePath) return null;
    
    // 이미 전체 URL인 경우
    if (imagePath.startsWith('http')) {
      return appendAuthParams(imagePath);
    }
    // API URL과 결합 필요한 경우
    const baseUrl = process.env.NEXT_PUBLIC_API_URL || '';
    if (imagePath.startsWith('/')) {
      return appendAuthParams(`${baseUrl}${imagePath}`);
    }
    return appendAuthParams(`${baseUrl}/api/files/view/${encodeURIComponent(imagePath)}`);
  }, [src, appendAuthParams]);

  // persistent 모드: 프로필 이미지 URL 처리
  useEffect(() => {
    if (!persistent) return;

    const imageUrl = getImageUrl(user?.profileImage);
    if (imageUrl && imageUrl !== currentImage) {
      setImageError(false);
      setCurrentImage(imageUrl);
    } else if (!imageUrl) {
      setCurrentImage('');
    }
  }, [persistent, user?.profileImage, getImageUrl, currentImage]);

  // persistent 모드: 전역 프로필 업데이트 리스너
  useEffect(() => {
    if (!persistent) return;

    const handleProfileUpdate = () => {
      try {
        const updatedUser = JSON.parse(localStorage.getItem('user') || '{}');
        // 현재 사용자의 프로필이 업데이트된 경우에만 이미지 업데이트
        if (user?.id === updatedUser.id && updatedUser.profileImage !== user.profileImage) {
          const newImageUrl = getImageUrl(updatedUser.profileImage);
          setImageError(false);
          setCurrentImage(newImageUrl);
        }
      } catch (error) {
        console.error('Profile update handling error:', error);
      }
    };
    
    window.addEventListener('userProfileUpdate', handleProfileUpdate);
    return () => {
      window.removeEventListener('userProfileUpdate', handleProfileUpdate);
    };
  }, [persistent, getImageUrl, user?.id, user?.profileImage]);

  // 이미지 에러 핸들러
  const handleImageError = useCallback((e) => {
    if (!persistent) return;
    
    e.preventDefault();
    setImageError(true);

    console.debug('Avatar image load failed:', {
      user: user?.name,
      email: user?.email,
      imageUrl: persistent ? currentImage : getImageUrl(user?.profileImage)
    });
  }, [persistent, currentImage, user?.name, user?.email, user?.profileImage, getImageUrl]);

  // 최종 이미지 URL 결정
  const finalImageUrl = (() => {
    if (!showImage) return undefined;
    
    if (persistent) {
      return currentImage && !imageError ? currentImage : undefined;
    }
    
    return getImageUrl(user?.profileImage);
  })();

  // 사용자 이름 첫 글자
  const initial = showInitials ? (user?.name?.charAt(0)?.toUpperCase() || '?') : '';

  // 클릭 가능한 경우 button으로 렌더링
  const renderProp = onClick ? <button onClick={onClick} /> : undefined;

  return (
    <Avatar.Root
      ref={ref}
      key={user?._id || user?.id}
      shape="circle"
      size={size}
      render={renderProp}
      src={finalImageUrl}
      className={className}
      style={{
        backgroundColor,
        color,
        cursor: onClick ? 'pointer' : 'default',
        ...style
      }}
      {...props}
    >
      {finalImageUrl && (
        <Avatar.ImagePrimitive 
          onError={persistent ? handleImageError : undefined}
          alt={`${user?.name}'s profile`}
        />
      )}
      <Avatar.FallbackPrimitive style={{ backgroundColor, color, fontWeight: '500' }}>
        {initial}
      </Avatar.FallbackPrimitive>
    </Avatar.Root>
  );
});

CustomAvatar.displayName = 'CustomAvatar';

export default CustomAvatar;
