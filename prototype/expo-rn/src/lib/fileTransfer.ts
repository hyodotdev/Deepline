import * as FileSystem from 'expo-file-system/legacy';

const BINARY_CONTENT_TYPE = 'application/octet-stream';
const DOWNLOAD_DIR = `${FileSystem.documentDirectory ?? ''}deepline-downloads`;

type UploadResponse = {
  storageId: string;
};

async function ensureDownloadDir() {
  if (!FileSystem.documentDirectory) {
    throw new Error('No document directory is available for decrypted attachments.');
  }

  await FileSystem.makeDirectoryAsync(DOWNLOAD_DIR, {
    intermediates: true,
  });
}

export async function uploadEncryptedFile(
  uploadUrl: string,
  fileUri: string,
): Promise<string> {
  const response = await FileSystem.uploadAsync(uploadUrl, fileUri, {
    headers: {
      'Content-Type': BINARY_CONTENT_TYPE,
    },
    httpMethod: 'POST',
    uploadType: FileSystem.FileSystemUploadType.BINARY_CONTENT,
  });

  if (response.status < 200 || response.status >= 300) {
    throw new Error(`Encrypted upload failed with status ${response.status}.`);
  }

  const parsed = JSON.parse(response.body) as Partial<UploadResponse>;

  if (!parsed.storageId || typeof parsed.storageId !== 'string') {
    throw new Error('Convex upload did not return a storage identifier.');
  }

  return parsed.storageId;
}

export async function downloadEncryptedFile(
  downloadUrl: string,
  extension = 'bin',
): Promise<string> {
  await ensureDownloadDir();

  const ciphertextUri = `${DOWNLOAD_DIR}/${crypto.randomUUID()}.${extension}`;
  const result = await FileSystem.downloadAsync(downloadUrl, ciphertextUri);

  if (result.status !== 200) {
    throw new Error(`Encrypted download failed with status ${result.status}.`);
  }

  return result.uri;
}

export async function buildDecryptedAttachmentPath(fileName: string): Promise<string> {
  await ensureDownloadDir();

  const normalizedName = fileName.replace(/[^a-zA-Z0-9._-]/g, '_');
  return `${DOWNLOAD_DIR}/${crypto.randomUUID()}-${normalizedName || 'attachment'}`;
}
