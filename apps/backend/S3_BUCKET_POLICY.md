# S3 버킷 정책 설정 가이드

이 애플리케이션은 업로드된 파일(이미지, 문서 등)을 S3에 저장하고, 프런트엔드에서 직접 S3 URL로 접근합니다.

## 필수 설정: S3 버킷 Public Read 정책

S3 버킷에 아래 정책을 적용하여 업로드된 파일을 public read 가능하게 설정해야 합니다.

### 1. AWS 콘솔에서 설정하기

1. [S3 콘솔](https://console.aws.amazon.com/s3/)로 이동
2. 버킷 선택
3. **권한(Permissions)** 탭 클릭
4. **퍼블릭 액세스 차단(Block public access)** 섹션에서 **편집** 클릭
5. **"새 퍼블릭 버킷 또는 액세스 지점 정책을 통해 부여된 버킷 및 객체에 대한 퍼블릭 액세스 차단"** 체크 해제
6. **변경 사항 저장**
7. **버킷 정책(Bucket Policy)** 섹션에서 **편집** 클릭
8. 아래 JSON 정책 붙여넣기 (버킷명 수정 필요)

### 2. 버킷 정책 JSON

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadGetObject",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::YOUR-BUCKET-NAME/*"
    }
  ]
}
```

**중요:** `YOUR-BUCKET-NAME`을 실제 버킷 이름으로 변경하세요.

### 3. 특정 경로만 Public Read 허용 (권장)

보안을 위해 특정 경로만 public read를 허용할 수 있습니다:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublicReadChatFiles",
      "Effect": "Allow",
      "Principal": "*",
      "Action": "s3:GetObject",
      "Resource": [
        "arn:aws:s3:::YOUR-BUCKET-NAME/chat/files/*",
        "arn:aws:s3:::YOUR-BUCKET-NAME/chat/profiles/*"
      ]
    }
  ]
}
```

이 정책은 `chat/files/`와 `chat/profiles/` 경로의 파일만 public read를 허용합니다.

## application.yml 설정

`apps/backend/src/main/resources/application.yml` 또는 `.env` 파일에 S3 설정:

```yaml
storage:
  s3:
    bucket: your-bucket-name
    region: ap-northeast-2
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}
    base-path: chat  # 버킷 내 기본 경로
    default-folder: files  # 채팅 파일 저장 경로
```

또는 `.env` 파일:

```
STORAGE_S3_BUCKET=your-bucket-name
STORAGE_S3_REGION=ap-northeast-2
STORAGE_S3_ACCESS_KEY=your-access-key
STORAGE_S3_SECRET_KEY=your-secret-key
STORAGE_S3_BASE_PATH=chat
STORAGE_S3_DEFAULT_FOLDER=files
```

## CORS 설정

프런트엔드에서 S3에 직접 접근하려면 CORS 설정도 필요합니다.

### S3 버킷 CORS 설정

1. S3 콘솔에서 버킷 선택
2. **권한(Permissions)** 탭 클릭
3. **CORS(Cross-origin resource sharing)** 섹션에서 **편집** 클릭
4. 아래 JSON 붙여넣기:

```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["GET", "HEAD"],
    "AllowedOrigins": [
      "http://localhost:3000",
      "https://your-frontend-domain.com"
    ],
    "ExposeHeaders": [
      "ETag",
      "Content-Type",
      "Content-Length"
    ],
    "MaxAgeSeconds": 3000
  }
]
```

**중요:** `AllowedOrigins`에 실제 프런트엔드 도메인을 추가하세요.

## 보안 고려사항

### 1. Public Read의 위험성

- **모든 사람이 파일 URL만 알면 접근 가능**
- 민감한 정보는 절대 업로드하지 말 것
- 사용자 프로필 이미지, 채팅 이미지 등 공개 가능한 파일만 업로드

### 2. 대안: CloudFront + Signed URL (프로덕션 권장)

프로덕션 환경에서는 CloudFront + Signed URL을 사용하는 것이 더 안전합니다:

1. CloudFront 배포 생성 (S3 버킷을 오리진으로)
2. S3 버킷을 private으로 유지
3. CloudFront에서만 S3 접근 가능하도록 설정 (OAI 사용)
4. 백엔드에서 CloudFront Signed URL 생성

이 방법은 추가 구현이 필요하지만, 파일 접근을 더 세밀하게 제어할 수 있습니다.

### 3. 파일 업로드 크기 제한

현재 설정:
- 이미지: 10MB
- PDF: 20MB
- 전체 파일: 50MB

`FileService.java`와 `application.yml`에서 조정 가능합니다.

## 테스트

설정 후 테스트:

1. 백엔드 재시작
2. 프런트엔드에서 이미지 업로드
3. 브라우저 개발자 도구 > Network 탭에서 이미지 URL 확인
4. URL이 `https://YOUR-BUCKET-NAME.s3.REGION.amazonaws.com/...` 형식인지 확인
5. 새 탭에서 URL 직접 접근 시 이미지가 표시되는지 확인

## 문제 해결

### "Access Denied" 오류

- 버킷 정책이 올바르게 설정되었는지 확인
- 퍼블릭 액세스 차단 설정 확인
- Resource ARN이 올바른지 확인 (버킷명, 경로)

### CORS 오류

- CORS 설정에 프런트엔드 도메인이 포함되어 있는지 확인
- AllowedMethods에 GET이 포함되어 있는지 확인

### 이미지가 로드되지 않음

1. Network 탭에서 실제 요청 URL 확인
2. URL을 새 탭에서 직접 열어서 접근 가능한지 확인
3. S3FileService.java의 getS3PublicUrl() 메서드가 올바른 URL을 생성하는지 확인
