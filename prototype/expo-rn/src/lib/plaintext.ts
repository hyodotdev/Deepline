const FORBIDDEN_PLAINTEXT_KEYS = new Set([
  'body',
  'plaintext',
  'plaintextbody',
  'messagebody',
  'filename',
  'groupname',
  'grouptitle',
  'contactname',
  'rawcontact',
  'rawcontacts',
  'phone',
  'phonenumber',
  'email',
]);

function normalizeKey(value: string): string {
  return value.replace(/[^a-z]/gi, '').toLowerCase();
}

export function findForbiddenPlaintextFields(
  value: unknown,
  path = '',
): string[] {
  if (!value || typeof value !== 'object') {
    return [];
  }

  if (Array.isArray(value)) {
    return value.flatMap((entry, index) =>
      findForbiddenPlaintextFields(entry, `${path}[${index}]`),
    );
  }

  return Object.entries(value as Record<string, unknown>).flatMap(
    ([key, nestedValue]) => {
      const nextPath = path ? `${path}.${key}` : key;

      if (
        FORBIDDEN_PLAINTEXT_KEYS.has(normalizeKey(key)) &&
        nestedValue !== undefined &&
        nestedValue !== null &&
        String(nestedValue).trim()
      ) {
        return [nextPath];
      }

      return findForbiddenPlaintextFields(nestedValue, nextPath);
    },
  );
}

export function assertNoPlaintextFields(
  label: string,
  value: unknown,
): void {
  const matches = findForbiddenPlaintextFields(value);

  if (matches.length > 0) {
    throw new Error(
      `${label} contains forbidden plaintext fields: ${matches.join(', ')}`,
    );
  }
}

export function isOpaqueTransportValue(value: string): boolean {
  return /^[A-Za-z0-9+/=_-]{24,}$/.test(value) && !/\s/.test(value);
}

export function assertOpaqueTransportValue(
  label: string,
  value: string,
): void {
  if (!isOpaqueTransportValue(value)) {
    throw new Error(`${label} must be an opaque encrypted or encoded value.`);
  }
}
