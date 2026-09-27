INSERT INTO `sso_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `is_visible`, `menu_icon`, `menu_code`, `menu_level`, `menu_sort`, `route_path`, `component`, `permissions`, `is_external`, `active_menu`, `is_keepalive`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES 
(REPLACE(UUID(), '-', ''), '', '流程表单', 0, 1, 'ant-design:apartment-outline', '00004', 1, 998, '/workflow', NULL, '', 0, NULL, 0, '流程表单目录', 'admin', NOW(), 'admin', NOW());

SET @parentId = (SELECT id FROM sso_menu WHERE route_path = '/workflow' LIMIT 1);

INSERT INTO `sso_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `is_visible`, `menu_icon`, `menu_code`, `menu_level`, `menu_sort`, `route_path`, `component`, `permissions`, `is_external`, `active_menu`, `is_keepalive`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES 
(REPLACE(UUID(), '-', ''), @parentId, '流程图', 1, 1, 'ant-design:apartment-outline', '0000400001', 2, 1, '/workflow/flowChart', '/demo/flow-chart/index.vue', 'demo:flowChart:query', 0, NULL, 1, '流程图菜单', 'admin', NOW(), 'admin', NOW()),
(REPLACE(UUID(), '-', ''), @parentId, '表单设计器', 1, 1, 'ant-design:form-outlined', '0000400002', 2, 2, '/workflow/formDesigner', '/demo/form-designer/index.vue', 'demo:formDesigner:query', 0, NULL, 1, '表单设计器菜单', 'admin', NOW(), 'admin', NOW());

INSERT INTO `sso_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `is_visible`, `menu_icon`, `menu_code`, `menu_level`, `menu_sort`, `route_path`, `component`, `permissions`, `is_external`, `active_menu`, `is_keepalive`, `remark`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES 
(REPLACE(UUID(), '-', ''), '', '关于', 1, 1, 'simple-icons:aboutdotme', '00005', 1, 999, '/about', '/sys/about/index.vue', 'sys:about:query', 0, NULL, 1, '关于菜单', 'admin', NOW(), 'admin', NOW());
