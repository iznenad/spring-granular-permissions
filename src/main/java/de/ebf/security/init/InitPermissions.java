package de.ebf.security.init;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaQuery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.Transactional;

import de.ebf.security.exceptions.MoreThanOnePermissionModelFoundException;
import de.ebf.security.exceptions.MoreThanOnePermissionNameFieldFoundException;
import de.ebf.security.exceptions.NoPermissionFieldNameFoundException;
import de.ebf.security.exceptions.NoPermissionModelFoundException;
import de.ebf.security.internal.data.PermissionModelDefinition;
import de.ebf.security.internal.permission.BasicPermission;
import de.ebf.security.internal.permission.InternalPermission;
import de.ebf.security.internal.services.PermissionModelFinder;
import de.ebf.security.internal.services.PermissionModelOperations;
import de.ebf.security.scanner.PermissionScanner;

@Transactional
public class InitPermissions implements ApplicationListener<ContextRefreshedEvent>, Ordered {

    private static final Logger logger = LoggerFactory.getLogger(InitPermissions.class);

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ConfigurableApplicationContext applicationContext = (ConfigurableApplicationContext) event
                .getApplicationContext();

        PermissionModelFinder permissionModelFinder = applicationContext.getBean(PermissionModelFinder.class);

        PermissionModelDefinition permissionModelDefinition;
        try {
            permissionModelDefinition = permissionModelFinder.find();
        } catch (MoreThanOnePermissionModelFoundException | NoPermissionModelFoundException
                | NoPermissionFieldNameFoundException | MoreThanOnePermissionNameFieldFoundException e) {
            logger.error("Permission model not well defined, cannot store permissions. Permission system won't work.",
                    e);

            applicationContext.close();
            applicationContext.stop();
            return;
        }

        PermissionScanner permissionScanner = applicationContext.getBean(PermissionScanner.class);
        EntityManager entityManager = applicationContext.getBean(EntityManager.class);
        PermissionModelOperations permissionModelOperations = applicationContext
                .getBean(PermissionModelOperations.class);

        Set<InternalPermission> permissions = permissionScanner.scan();

        CriteriaQuery selectAll = entityManager.getCriteriaBuilder()
                .createQuery(permissionModelDefinition.getPermissionModelClass());
        selectAll.select(selectAll.from(permissionModelDefinition.getPermissionModelClass()));
        List<Object> existingPermissionRecords = entityManager.createQuery(selectAll).getResultList();

        Set<InternalPermission> existingPermissions = existingPermissionRecords.stream()
                .map(new Function<Object, InternalPermission>() {
                    @Override
                    public InternalPermission apply(Object permissionRecord) {

                        String permissionName = permissionModelOperations.getName(permissionModelDefinition,
                                permissionRecord);

                        return new BasicPermission(permissionName);

                    }
                }).collect(Collectors.toSet());

        permissions.forEach(fun -> {
            logger.info("Registering permission: {}", fun.getName());

            if (existingPermissions.stream().anyMatch(existingFunction -> {
                return existingFunction.getName().equals(fun);
            })) {
                logger.info("Permission {} already exists.", fun);
                return;
            }

            Object permissionModelInstance = permissionModelOperations.construct(permissionModelDefinition, fun);

            entityManager.merge(permissionModelInstance);
        });

    }

    @Override
    public int getOrder() {
        return 0;
    }

}
