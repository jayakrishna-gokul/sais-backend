package com.sapphireai.service.vector;

import com.sapphireai.model.entity.DocumentChunk;
import com.sapphireai.repository.CollectionMetadataRepository;
import com.sapphireai.service.embedding.EmbeddingService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.CreateCollection;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import io.qdrant.client.grpc.Points.Condition;
import io.qdrant.client.grpc.Points.FieldCondition;
import io.qdrant.client.grpc.Points.Filter;
import io.qdrant.client.grpc.Points.Match;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.Vectors;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import io.qdrant.client.grpc.Points.RetrievedPoint;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Qdrant vector database integration.
 * Stores chunk embeddings for similarity search.
 */
@Service
@Slf4j
public class VectorStoreService {

    private final QdrantClient qdrantClient;
    private final EmbeddingService embeddingService;
    private final String collectionName;
    private final int dimension;
    private final CollectionMetadataRepository metadataRepository;

    public VectorStoreService(
            @Value("${sapphire.vector.base-url}") String qdrantUrl,
            @Value("${sapphire.vector.collection-name}") String collectionName,
            @Value("${sapphire.embedding.dimension:384}") int dimension,
            CollectionMetadataRepository metadataRepository,
            EmbeddingService embeddingService
    ) {
        this.metadataRepository = metadataRepository;
        this.embeddingService = embeddingService;
        this.collectionName = collectionName;
        this.dimension = dimension;
        // Parse host and port from URL
        String host = qdrantUrl.replace("http://", "").replace("https://", "").split(":")[0];
        int port = Integer.parseInt(qdrantUrl.split(":")[2]);

        this.qdrantClient = new QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build());

        initializeCollection();
    }

    private void initializeCollection() {
        try {
            var collectionsResponse = qdrantClient.listCollectionsAsync().get();
            boolean exists = collectionsResponse.stream()
                    .anyMatch(c -> c.equals(collectionName));

            if (!exists) {
                log.info("Creating Qdrant collection: {}", collectionName);

                // AFTER (CORRECT - dynamic):
                int dimension = embeddingService.getEmbeddingDimension();
                VectorParams vectorParams = VectorParams.newBuilder()
                        .setSize(dimension)// ✅ Dynamic from embedder
                        .setDistance(io.qdrant.client.grpc.Collections.Distance.Cosine)
                        .build();

                log.info("Creating Qdrant collection: name={}, dimension={}, model={}",
                        collectionName, dimension, embeddingService.getActiveModel());

                CreateCollection createCollection = CreateCollection.newBuilder()
                        .setCollectionName(collectionName)
                        .setVectorsConfig(VectorsConfig.newBuilder()
                                .setParams(vectorParams)
                                .build())
                        .build();

                qdrantClient.createCollectionAsync(createCollection).get();
                log.info("Collection created successfully");
            }
        } catch (Exception e) {
            log.error("Failed to initialize Qdrant collection", e);
        }
    }

    public void indexChunks(List<DocumentChunk> chunks, String organizationId) {
        try {
            List<PointStruct> points = new ArrayList<>();

            for (DocumentChunk chunk : chunks) {
                float[] embedding = chunk.getEmbedding();
                if (embedding == null) {
                    throw new IllegalStateException("Chunk has no embedding. Call embedChunks() before indexing.");
                }

                // Use ValueFactory.value() - the official way!
                PointStruct point = PointStruct.newBuilder()
                        .setId(PointId.newBuilder().setUuid(chunk.getId()).build())
                        .setVectors(Vectors.newBuilder()
                                .setVector(io.qdrant.client.grpc.Points.Vector.newBuilder()
                                        .addAllData(convertFloatArrayToList(embedding))
                                        .build())
                                .build())
                        .putAllPayload(Map.of(
                                "chunk_id", io.qdrant.client.ValueFactory.value(chunk.getId()),
                                "document_id", io.qdrant.client.ValueFactory.value(chunk.getDocumentId()),
                                "organization_id", io.qdrant.client.ValueFactory.value(organizationId),
                                "content", io.qdrant.client.ValueFactory.value(chunk.getContent()),
                                "chunk_type", io.qdrant.client.ValueFactory.value(chunk.getChunkType())
                        ))
                        .build();

                points.add(point);
            }

            qdrantClient.upsertAsync(collectionName, points).get();
            log.info("Indexed {} chunks to Qdrant with org_id={}", chunks.size(), organizationId);
            log.info("DEBUG - First point payload: {}", points.get(0).getPayloadMap());

        } catch (Exception e) {
            log.error("Failed to index chunks", e);
            throw new RuntimeException("Vector indexing failed", e);
        }
    }

    public List<ScoredChunk> search(String query, String organizationId, int topK) {
        log.info("Vector search: query='{}', org='{}', topK={}", query, organizationId, topK);
        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            log.debug("Query embedding generated: dimension={}", queryEmbedding.length);
            log.info("DEBUG - Query embedding: dimension={}, first 5 values=[{}, {}, {}, {}, {}]",
                    queryEmbedding.length,
                    queryEmbedding[0], queryEmbedding[1], queryEmbedding[2], queryEmbedding[3], queryEmbedding[4]);

            // VALIDATE DIMENSION ✅
            int collectionDimension = getCollectionDimension(collectionName);
            log.info("DEBUG - Collection dimension: {}", collectionDimension);

            if (queryEmbedding.length != collectionDimension) {
                throw new IllegalStateException(
                        String.format(
                                "Embedding dimension mismatch! Query embedding: %d, Collection '%s': %d. " +
                                        "Cannot search with incompatible embedding model.",
                                queryEmbedding.length, collectionName, collectionDimension
                        )
                );
            }

            // Search with organization filter
            Filter filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("organization_id")
                                    .setMatch(Match.newBuilder()
                                            .setKeyword(organizationId)
                                            .build())
                                    .build())
                            .build())
                    .build();

            SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(convertFloatArrayToList(queryEmbedding))
                    .setLimit(topK)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setFilter(filter)
                    .build();

            var results = qdrantClient.searchAsync(searchRequest).get();

            log.info("Vector search completed: found {} results", results.size());
            return results.stream()
                    .map(result -> new ScoredChunk(
                            result.getPayloadMap().get("chunk_id").getStringValue(),
                            result.getPayloadMap().get("content").getStringValue(),
                            result.getScore()
                    ))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Vector search failed", e);
            return Collections.emptyList();
        }
    }

    /**
     * Verify a single point exists in Qdrant by ID.
     * Used after indexing to confirm vectors actually persisted.
     */
    public boolean verifyChunkExists(String chunkId) {
        try {
            // Use scroll to check if the point exists
            // Qdrant 1.7.0 doesn't have a direct "get by ID" method,
            // so we search by chunk_id in payload
            Filter filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                                    .setKey("chunk_id")
                                    .setMatch(Match.newBuilder()
                                            .setKeyword(chunkId)
                                            .build())
                                    .build())
                            .build())
                    .build();

            SearchPoints searchRequest = SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(Collections.nCopies(dimension, 0.0f)) // Dummy vector
                    .setLimit(1)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(false).build())
                    .setFilter(filter)
                    .build();

            var results = qdrantClient.searchAsync(searchRequest).get();
            boolean exists = !results.isEmpty();

            if (!exists) {
                log.warn("Qdrant verification: chunk {} NOT FOUND in collection {}", chunkId, collectionName);
            }

            return exists;

        } catch (Exception e) {
            log.error("Qdrant verification failed for chunk {}: {}", chunkId, e.getMessage());
            return false;
        }
    }



    public record ScoredChunk(String chunkId, String content, float score) {}
    private List<Float> convertFloatArrayToList(float[] array) {
        List<Float> list = new ArrayList<>();
        for (float f : array) {
            list.add(f);
        }
        return list;
    }

    private int getCollectionDimension(String collectionName) {
        try {
            var collection = qdrantClient.getCollectionInfoAsync(collectionName).get();
            return (int) collection.getConfig()
                    .getParams()
                    .getVectorsConfig()
                    .getParams()
                    .getSize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to get collection dimension", e);
        }
    }

    /**
     * Delete all vectors for a specific document from Qdrant.
     * Used during cleanup when document processing fails.
     */
    public void deleteDocumentVectors(String documentId) {
        try {
            log.info("Deleting vectors for document: {}", documentId);

            // Build filter to match document_id
            Filter filter = Filter.newBuilder()
                    .addMust(Condition.newBuilder()
                            .setField(FieldCondition.newBuilder()
                            .setKey("document_id")
                            .setMatch(Match.newBuilder()
                                    .setKeyword(documentId)
                                    .build())
                            .build()))
                    .build();

            // Delete points matching the filter
            qdrantClient.deleteAsync(
                    collectionName,
                    filter,
                    null
            ).get();

            log.info("Deleted vectors for document: {}", documentId);

        } catch (Exception e) {
            log.error("Failed to delete vectors for document: {}", documentId, e);
            // Don't throw - cleanup should be best-effort
            log.warn("Vector cleanup failed but continuing with other cleanup steps");
        }
    }

    public void deleteCollection() {
        try {
            log.warn("DELETING COLLECTION: {}", collectionName);
            qdrantClient.deleteCollectionAsync(collectionName).get();
            log.warn("Collection deleted successfully");

            // Recreate it
            initializeCollection();
        } catch (Exception e) {
            log.error("Failed to delete collection", e);
        }
    }
}