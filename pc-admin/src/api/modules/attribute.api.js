import { USE_MOCK, req, pageTo } from '@/api/core';
import mock from '@/mock';

export const attributeApi = {
  attrTemplates: (categoryId) => USE_MOCK
    ? Promise.resolve(mock.attrTemplates.filter(t => t.categoryId === categoryId))
    : req('GET', '/api/admin/attr-templates', null, { categoryId }),
  saveAttrTemplate: (categoryId, d) => USE_MOCK ? Promise.resolve({ ok: true })
    : req('POST', '/api/admin/attr-template', null,
        { categoryId, name: d.name, options: d.options, required: d.required ? 1 : 0, sort: d.sort || 0 })
};
