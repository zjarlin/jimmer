package org.babyfish.jimmer;

import org.babyfish.jimmer.model.Immutables;
import org.babyfish.jimmer.model.ManualComment;
import org.babyfish.jimmer.model.RobotComment;
import org.babyfish.jimmer.model.Topic;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

public class PolymorphismTest {

    @Test
    public void test() {
        Topic topic = Immutables.createTopic(draft -> {
            draft.setName("I love GraphQL");
            draft.setPinnedComment(
                    new ManualComment(
                            "Mr Alex is the expert of GraphQL",
                            "admin-001"
                    )
            );
            draft.setComments(
                    Arrays.asList(
                            new RobotComment(
                                    "Be rational and polite",
                                    1
                            ),
                            new ManualComment(
                                    "Oh yeah",
                                    "guest-003"
                            )
                    )
            );
        });
        String json = topic.toString();
        Assertions.assertEquals(
                "{\"name\":\"I love GraphQL\"," +
                        "\"pinnedComment\":{\"type\":\"M\",\"text\":null,\"userId\":\"admin-001\"}," +
                        "\"comments\":[" +
                        "{\"type\":\"R\",\"text\":\"Be rational and polite\",\"level\":1}," +
                        "{\"type\":\"M\",\"text\":null,\"userId\":\"guest-003\"}" +
                        "]" +
                        "}",
                json
        );
        // TODO
    }
}
