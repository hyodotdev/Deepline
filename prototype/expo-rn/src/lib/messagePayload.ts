export type AttachmentEnvelope = {
  attachmentId: string;
  ciphertextByteLength: number;
  ciphertextDigest: string;
  fileName: string;
  keyBase64: string;
  mimeType: string;
  nonceBase64: string;
  plaintextByteLength: number;
  protocolVersion: string;
};

export type MessageEnvelope = {
  attachments: AttachmentEnvelope[];
  text: string;
  version: 'deepline-message-v1';
};

function isAttachmentEnvelope(value: unknown): value is AttachmentEnvelope {
  if (!value || typeof value !== 'object') {
    return false;
  }

  const candidate = value as Record<string, unknown>;

  return (
    typeof candidate.attachmentId === 'string' &&
    typeof candidate.ciphertextByteLength === 'number' &&
    typeof candidate.ciphertextDigest === 'string' &&
    typeof candidate.fileName === 'string' &&
    typeof candidate.keyBase64 === 'string' &&
    typeof candidate.mimeType === 'string' &&
    typeof candidate.nonceBase64 === 'string' &&
    typeof candidate.plaintextByteLength === 'number' &&
    typeof candidate.protocolVersion === 'string'
  );
}

export function serializeMessageEnvelope(input: {
  attachments?: AttachmentEnvelope[];
  text: string;
}): string {
  return JSON.stringify({
    attachments: input.attachments ?? [],
    text: input.text,
    version: 'deepline-message-v1',
  } satisfies MessageEnvelope);
}

export function deserializeMessageEnvelope(value: string): MessageEnvelope {
  try {
    const parsed = JSON.parse(value) as Partial<MessageEnvelope>;

    if (
      parsed?.version === 'deepline-message-v1' &&
      typeof parsed.text === 'string' &&
      Array.isArray(parsed.attachments) &&
      parsed.attachments.every(isAttachmentEnvelope)
    ) {
      return {
        attachments: parsed.attachments,
        text: parsed.text,
        version: 'deepline-message-v1',
      };
    }
  } catch {
    // Fall back to legacy plain-text demo messages.
  }

  return {
    attachments: [],
    text: value,
    version: 'deepline-message-v1',
  };
}
